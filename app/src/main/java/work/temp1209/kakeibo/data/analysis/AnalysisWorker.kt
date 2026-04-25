package work.temp1209.kakeibo.data.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import work.temp1209.kakeibo.data.db.GeminiResultEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.ui.notifications.AnalysisNotifications
import java.time.Instant
import java.util.UUID

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).receiptDao()
        val apiKey = GeminiApiKeyStore(applicationContext).readKeyOrNull() ?: return Result.success()
        val gemini = GeminiClient()

        while (true) {
            val entry = dao.getNextQueuedOrNull() ?: break
            val now = Instant.now().toString()
            dao.markQueueRunning(queueId = entry.queueId, startedAt = now)

            val attempt = entry.attemptCount + 1
            try {
                val img = dao.getReceiptImage(entry.receiptId) ?: error("receipt image missing")
                val jpegBytes = applicationContext.contentResolver.openInputStream(android.net.Uri.parse(img.localUri))
                    ?.use { it.readBytes() }
                    ?: error("failed to read image")

                val rawResponse = gemini.generateStrictJsonFromImage(
                    apiKey = apiKey,
                    jpegBytes = jpegBytes,
                    prompt = PROMPT,
                    responseJsonSchema = ReceiptJsonSchema.schemaV1(),
                )

                val strictJson = extractStrictJson(rawResponse)
                val parsed = JSONObject(strictJson)
                val receipt = parsed.getJSONObject("receipt")
                val itemsArr = parsed.getJSONArray("items")

                val merchantName = receipt.getString("merchantName")
                val receiptDatetime = receipt.getString("receiptDatetime")
                val totalAmountYen = receipt.getLong("totalAmountYen")
                val paymentMethod = receipt.optString("paymentMethod").takeIf { it.isNotBlank() }
                val paymentServiceName = receipt.optString("paymentServiceName").takeIf { it.isNotBlank() }

                val items = mutableListOf<ReceiptItemEntity>()
                var subtotal = 0L
                for (i in 0 until itemsArr.length()) {
                    val it = itemsArr.getJSONObject(i)
                    val lineIndex = it.getInt("lineIndex")
                    val itemName = it.getString("itemName")
                    val quantity = it.getInt("quantity")
                    val lineTotalYen = it.getLong("lineTotalYen")
                    val categoryMajor = it.getString("categoryMajor")
                    val categoryMinor = it.getString("categoryMinor")
                    val necessityScore = it.getInt("necessityScore")
                    val confidence = it.getDouble("confidence")

                    subtotal += lineTotalYen
                    items += ReceiptItemEntity(
                        itemId = "${entry.receiptId}:$lineIndex",
                        receiptId = entry.receiptId,
                        lineIndex = lineIndex,
                        itemName = itemName,
                        quantity = quantity,
                        lineTotalYen = lineTotalYen,
                        categoryMajor = categoryMajor,
                        categoryMinor = categoryMinor,
                        necessityScore = necessityScore,
                        confidence = confidence,
                        isAdjustment = 0,
                    )
                }

                val adjustment = totalAmountYen - subtotal
                if (adjustment != 0L) {
                    items += ReceiptItemEntity(
                        itemId = "${entry.receiptId}:adjustment",
                        receiptId = entry.receiptId,
                        lineIndex = items.size,
                        itemName = "調整",
                        quantity = 1,
                        lineTotalYen = adjustment,
                        categoryMajor = "OTHER",
                        categoryMinor = "その他",
                        necessityScore = 50,
                        confidence = 1.0,
                        isAdjustment = 1,
                    )
                }

                val existing = dao.getReceiptOrNull(entry.receiptId) ?: error("receipt missing")
                val updatedAt = Instant.now().toString()
                dao.upsertReceipt(
                    existing.copy(
                        receiptDatetime = receiptDatetime,
                        merchantName = merchantName,
                        totalAmountYen = totalAmountYen,
                        paymentMethod = paymentMethod,
                        paymentServiceName = paymentServiceName,
                        analysisStatus = "DONE",
                        analysisStartedAt = now,
                        analysisCompletedAt = updatedAt,
                        analysisErrorMessage = null,
                        needsReview = 0,
                        itemsSubtotalYen = subtotal,
                        adjustmentYen = adjustment,
                        updatedAt = updatedAt,
                    )
                )

                dao.upsertReceiptItems(items)
                dao.insertGeminiResult(
                    GeminiResultEntity(
                        resultId = UUID.randomUUID().toString(),
                        receiptId = entry.receiptId,
                        schemaVersion = parsed.getString("schemaVersion"),
                        model = "gemini-2.5-flash",
                        rawJson = strictJson,
                        createdAt = updatedAt,
                    )
                )

                dao.finishQueue(
                    queueId = entry.queueId,
                    status = "DONE",
                    finishedAt = updatedAt,
                    lastError = null,
                    attemptCount = attempt,
                )
                AnalysisNotifications.notifyDone(applicationContext, entry.receiptId)
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                if (attempt <= 1) {
                    // Put back to QUEUED for a single retry with WorkManager backoff.
                    dao.requeue(entry.queueId, attemptCount = attempt, lastError = msg)
                    return Result.retry()
                } else {
                    val finishedAt = Instant.now().toString()
                    val existing = dao.getReceiptOrNull(entry.receiptId)
                    if (existing != null) {
                        dao.upsertReceipt(
                            existing.copy(
                                analysisStatus = "FAILED",
                                analysisStartedAt = existing.analysisStartedAt ?: now,
                                analysisCompletedAt = finishedAt,
                                analysisErrorMessage = msg,
                                needsReview = 1,
                                updatedAt = finishedAt,
                            )
                        )
                    }
                    dao.finishQueue(entry.queueId, status = "FAILED", finishedAt = finishedAt, lastError = msg, attemptCount = attempt)
                    AnalysisNotifications.notifyFailed(applicationContext, entry.receiptId)
                }
            }
        }

        return Result.success()
    }

    private fun extractStrictJson(rawResponse: String): String {
        // generateContent response: candidates[0].content.parts[0].text
        val root = JSONObject(rawResponse)
        val candidates = root.getJSONArray("candidates")
        if (candidates.length() == 0) error("no candidates")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        if (parts.length() == 0) error("no parts")
        val text = parts.getJSONObject(0).optString("text")
        if (text.isBlank()) error("empty response text")
        return text.trim()
    }

    companion object {
        private const val PROMPT = """
レシート画像から情報を抽出し、指定スキーマの厳格JSONのみを返してください。
カテゴリminorは許容値に完全一致させてください。曖昧な場合はそれでも最も近い値を選び、confidenceを低くしてください。
"""
    }
}

