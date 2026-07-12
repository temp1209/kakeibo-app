package work.temp1209.kakeibo.data.analysis

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import work.temp1209.kakeibo.data.analysis.model.ReceiptItem
import work.temp1209.kakeibo.data.db.AnalysisQueueEntity
import work.temp1209.kakeibo.data.db.GeminiResultEntity
import work.temp1209.kakeibo.data.db.ReceiptDao
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.gemini.GeminiResponseParser
import work.temp1209.kakeibo.data.gemini.GeminiUserMessages
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.data.prefs.NecessityPolicyStore
import work.temp1209.kakeibo.data.prompt.ReceiptAnalysisPrompt
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.notifications.NotificationHistory
import work.temp1209.kakeibo.ui.notifications.AnalysisNotifications
import java.time.Instant
import java.util.UUID

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).receiptDao()
        val gemini = GeminiClient()

        Log.d(TAG, "doWork start")
        while (true) {
            val entry = dao.getNextQueuedOrNull() ?: break
            val apiKey = GeminiApiKeyStore(applicationContext).readKeyOrNull()
            if (apiKey == null) {
                handleMissingApiKeyFailure(dao = dao, entry = entry)
                continue
            }

            val now = Instant.now().toString()
            Log.d(TAG, "dequeue queueId=${entry.queueId} receiptId=${entry.receiptId} attemptCount=${entry.attemptCount}")
            dao.markQueueRunning(queueId = entry.queueId, startedAt = now)

            val attempt = entry.attemptCount + 1
            val head = dao.getReceiptOrNull(entry.receiptId)
            if (head?.deletedAt != null) {
                val skipAt = Instant.now().toString()
                Log.d(TAG, "skip queue: receipt deleted receiptId=${entry.receiptId}")
                dao.finishQueue(
                    queueId = entry.queueId,
                    status = "DONE",
                    finishedAt = skipAt,
                    lastError = null,
                    attemptCount = attempt,
                )
                continue
            }

            try {
                val img = dao.getReceiptImage(entry.receiptId) ?: error("receipt image missing")
                val jpegBytes = applicationContext.contentResolver.openInputStream(android.net.Uri.parse(img.localUri))
                    ?.use { it.readBytes() }
                    ?: error("failed to read image")
                Log.d(TAG, "loaded image bytes=${jpegBytes.size}")

                val prompt = buildPrompt(applicationContext)
                val rawResponse = gemini.generateStrictJsonFromImage(
                    apiKey = apiKey,
                    jpegBytes = jpegBytes,
                    prompt = prompt,
                    responseJsonSchema = ReceiptJsonSchema.schemaV1(),
                )

                val strictJson = extractStrictJson(rawResponse)
                val parsed = try {
                    GeminiStrictParser.parseStrictJson(strictJson)
                } catch (_: NoReceiptInImageException) {
                    handleNoReceiptFailure(
                        dao = dao,
                        entry = entry,
                        strictJson = strictJson,
                        now = now,
                        attempt = attempt,
                    )
                    continue
                }
                val flags = GeminiStrictParser.reviewFlags(parsed, confidenceThreshold = 0.7)
                Log.d(TAG, "parsed strictJson items=${parsed.items.size} needsReview=${flags.needsReview}")

                val merchantName = parsed.receipt.merchantName
                val receiptDatetime = parsed.receipt.receiptDatetime
                val totalAmountYen = parsed.receipt.totalAmountYen
                val paymentMethod = parsed.receipt.paymentMethod
                val paymentServiceName = parsed.receipt.paymentServiceName

                val items = mutableListOf<ReceiptItemEntity>()
                var subtotal = 0L
                val seenLineIndex = hashSetOf<Int>()
                val normalizedItems = parsed.items
                    .sortedBy { it.lineIndex }
                    .filter { it.lineIndex >= 0 }
                    .filter { seenLineIndex.add(it.lineIndex) }

                val dropped = parsed.items.size - normalizedItems.size
                val flags2 = if (dropped > 0) {
                    flags.copy(needsReview = true, reasons = (flags.reasons + "duplicate/invalid lineIndex dropped: $dropped").distinct())
                } else {
                    flags
                }

                for (it: ReceiptItem in normalizedItems) {
                    subtotal += it.lineTotalYen
                    items += ReceiptItemEntity(
                        itemId = "${entry.receiptId}:${it.lineIndex}",
                        receiptId = entry.receiptId,
                        lineIndex = it.lineIndex,
                        itemName = it.itemName,
                        quantity = it.quantity,
                        lineTotalYen = it.lineTotalYen,
                        categoryMajor = it.categoryMajor,
                        categoryMinor = it.categoryMinor,
                        necessityScore = it.necessityScore,
                        confidence = it.confidence,
                        isAdjustment = 0,
                    )
                }

                val adjustment = totalAmountYen - subtotal
                if (adjustment != 0L) {
                    val maxLineIndex = normalizedItems.maxOfOrNull { it.lineIndex } ?: -1
                    items += ReceiptItemEntity(
                        itemId = "${entry.receiptId}:adjustment",
                        receiptId = entry.receiptId,
                        lineIndex = maxLineIndex + 1,
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
                val receiptStatus = if (flags2.needsReview) "NEEDS_REVIEW" else "DONE"
                val errorMessage = if (flags2.needsReview) flags2.reasons.joinToString("; ") else null
                val updatedReceipt = existing.copy(
                    receiptDatetime = receiptDatetime,
                    merchantName = merchantName,
                    totalAmountYen = totalAmountYen,
                    paymentMethod = paymentMethod,
                    paymentServiceName = paymentServiceName,
                    analysisStatus = receiptStatus,
                    analysisStartedAt = now,
                    analysisCompletedAt = updatedAt,
                    analysisErrorMessage = errorMessage,
                    needsReview = if (flags2.needsReview) 1 else 0,
                    itemsSubtotalYen = subtotal,
                    adjustmentYen = adjustment,
                    updatedAt = updatedAt,
                )
                dao.upsertReceipt(updatedReceipt)

                dao.upsertReceiptItems(items)
                dao.insertGeminiResult(
                    GeminiResultEntity(
                        resultId = UUID.randomUUID().toString(),
                        receiptId = entry.receiptId,
                        schemaVersion = parsed.schemaVersion,
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
                recordOutcomeAndMaybeNotify(
                    receipt = updatedReceipt,
                    eventType = if (flags2.needsReview) {
                        NotificationHistory.TYPE_NEEDS_REVIEW
                    } else {
                        NotificationHistory.TYPE_DONE
                    },
                    notify = {
                        if (flags2.needsReview) {
                            AnalysisNotifications.notifyNeedsReview(applicationContext, entry.receiptId)
                        } else {
                            AnalysisNotifications.notifyDone(applicationContext, entry.receiptId)
                        }
                    },
                    notifyGateReceipt = existing,
                )
                Log.d(TAG, "done receiptId=${entry.receiptId} status=$receiptStatus subtotal=$subtotal adjustment=$adjustment")
            } catch (e: Exception) {
                val msg = GeminiUserMessages.userFacingError(e, GeminiUserMessages.Operation.RECEIPT_ANALYSIS)
                Log.w(TAG, "failed receiptId=${entry.receiptId} attempt=$attempt error=$msg", e)
                val finishedAt = Instant.now().toString()
                val existing = dao.getReceiptOrNull(entry.receiptId)
                val failedReceipt = existing?.copy(
                    analysisStatus = "FAILED",
                    analysisStartedAt = existing.analysisStartedAt ?: now,
                    analysisCompletedAt = finishedAt,
                    analysisErrorMessage = msg,
                    needsReview = 1,
                    updatedAt = finishedAt,
                )
                if (failedReceipt != null) {
                    dao.upsertReceipt(failedReceipt)
                }
                dao.finishQueue(
                    queueId = entry.queueId,
                    status = "FAILED",
                    finishedAt = finishedAt,
                    lastError = msg,
                    attemptCount = attempt,
                )
                if (failedReceipt != null) {
                    recordOutcomeAndMaybeNotify(
                        receipt = failedReceipt,
                        eventType = NotificationHistory.TYPE_FAILED,
                        notify = { AnalysisNotifications.notifyFailed(applicationContext, entry.receiptId) },
                        notifyGateReceipt = existing,
                    )
                }
            }
        }

        Log.d(TAG, "doWork end (no more queued)")
        return Result.success()
    }

    private suspend fun handleMissingApiKeyFailure(
        dao: ReceiptDao,
        entry: AnalysisQueueEntity,
    ) {
        val now = Instant.now().toString()
        val attempt = entry.attemptCount + 1
        val message = GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE
        Log.w(TAG, "api key missing receiptId=${entry.receiptId}")
        dao.markQueueRunning(queueId = entry.queueId, startedAt = now)
        val existing = dao.getReceiptOrNull(entry.receiptId)
        val failedReceipt = existing?.copy(
            analysisStatus = "FAILED",
            analysisStartedAt = existing.analysisStartedAt ?: now,
            analysisCompletedAt = now,
            analysisErrorMessage = message,
            needsReview = 1,
            updatedAt = now,
        )
        if (failedReceipt != null) {
            dao.upsertReceipt(failedReceipt)
        }
        dao.finishQueue(
            queueId = entry.queueId,
            status = "FAILED",
            finishedAt = now,
            lastError = message,
            attemptCount = attempt,
        )
        if (failedReceipt != null) {
            recordOutcomeAndMaybeNotify(
                receipt = failedReceipt,
                eventType = NotificationHistory.TYPE_FAILED,
                notify = { AnalysisNotifications.notifyFailed(applicationContext, entry.receiptId) },
                notifyGateReceipt = existing,
            )
        }
    }

    /**
     * モデルが `[NO_RECEIPT]` を返した場合: リトライせず解析失敗として確定する（生JSONは保存）。
     */
    private suspend fun handleNoReceiptFailure(
        dao: ReceiptDao,
        entry: AnalysisQueueEntity,
        strictJson: String,
        now: String,
        attempt: Int,
    ) {
        val finishedAt = Instant.now().toString()
        val receiptId = entry.receiptId
        val existing = dao.getReceiptOrNull(receiptId) ?: return
        val message = "画像にレシートが見当たりません。別の画像で試してください。"
        dao.insertGeminiResult(
            GeminiResultEntity(
                resultId = UUID.randomUUID().toString(),
                receiptId = receiptId,
                schemaVersion = "1.0",
                model = "gemini-2.5-flash",
                rawJson = strictJson,
                createdAt = finishedAt,
            ),
        )
        val failedReceipt = existing.copy(
            analysisStatus = "FAILED",
            analysisStartedAt = existing.analysisStartedAt ?: now,
            analysisCompletedAt = finishedAt,
            analysisErrorMessage = message,
            needsReview = 1,
            updatedAt = finishedAt,
        )
        dao.upsertReceipt(failedReceipt)
        dao.finishQueue(
            queueId = entry.queueId,
            status = "FAILED",
            finishedAt = finishedAt,
            lastError = message,
            attemptCount = attempt,
        )
        recordOutcomeAndMaybeNotify(
            receipt = failedReceipt,
            eventType = NotificationHistory.TYPE_FAILED,
            notify = { AnalysisNotifications.notifyFailed(applicationContext, receiptId) },
            notifyGateReceipt = existing,
        )
        Log.w(TAG, "no receipt in image receiptId=$receiptId")
    }

    private suspend fun recordOutcomeAndMaybeNotify(
        receipt: ReceiptEntity,
        eventType: String,
        notify: () -> Unit,
        notifyGateReceipt: ReceiptEntity?,
    ) {
        NotificationHistory.record(applicationContext, receipt, eventType)
        if (shouldSendAnalysisOsNotification(notifyGateReceipt)) {
            notify()
        }
    }

    private fun extractStrictJson(rawResponse: String): String =
        GeminiResponseParser.extractResponseText(rawResponse)

    companion object {
        /**
         * 要件 P6-7: `MANUAL_NO_RECEIPT` では解析完了の OS 通知を出さない（キュー非投入が前提だが、万一の保険）。
         * `RECEIPT_CAMERA` / `EVIDENCE_IMAGE` は従来どおり。
         */
        private fun shouldSendAnalysisOsNotification(receipt: ReceiptEntity?): Boolean =
            receipt?.inputKind != "MANUAL_NO_RECEIPT"

        private const val TAG = "AnalysisWorker"

        private fun buildPrompt(context: Context): String =
            ReceiptAnalysisPrompt.buildFromStore(NecessityPolicyStore(context.applicationContext))
    }
}

