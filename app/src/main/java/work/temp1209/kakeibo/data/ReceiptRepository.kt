package work.temp1209.kakeibo.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.analysis.AnalysisWorker
import work.temp1209.kakeibo.data.analysis.NecessityRescoreWorker
import work.temp1209.kakeibo.data.notifications.NotificationHistory
import work.temp1209.kakeibo.data.notifications.NotificationHistoryEntry
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptImageEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.db.ReceiptListRow
import work.temp1209.kakeibo.data.domain.CategoryCatalog
import work.temp1209.kakeibo.data.domain.NecessityUtils
import work.temp1209.kakeibo.data.domain.ReceiptRequiredFields
import work.temp1209.kakeibo.data.necessity.CompiledNecessityPolicy
import work.temp1209.kakeibo.data.necessity.NecessityCorrection
import work.temp1209.kakeibo.data.necessity.NecessityCorrectionDirection
import work.temp1209.kakeibo.data.necessity.NecessityPolicyCompiler
import work.temp1209.kakeibo.data.necessity.NecessityPurposeId
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.data.prefs.NecessityPolicyStore
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ReceiptRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).receiptDao()

    suspend fun savePendingReceipt(
        imageUri: Uri,
        inputKind: String = "RECEIPT_CAMERA",
    ): String = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val receiptId = UUID.randomUUID().toString()
        Log.d(TAG, "savePendingReceipt start receiptId=$receiptId uri=${imageUri.scheme}")

        val (w, h) = runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                opts.outWidth to opts.outHeight
            }
        }.getOrNull()?.let { (ww, hh) -> ww to hh } ?: (null to null)

        val bytes = runCatching { imageUri.toFileOrNull()?.length() ?: 0L }.getOrNull() ?: 0L

        dao.upsertReceipt(
            ReceiptEntity(
                receiptId = receiptId,
                inputKind = inputKind,
                capturedAt = now,
                receiptDatetime = now, // 暫定: Phase1では capturedAt をコピー（後でGemini/手入力で上書き）
                analysisStatus = "PENDING",
                merchantName = null,
                totalAmountYen = null,
                paymentMethod = null,
                paymentServiceName = null,
                analysisStartedAt = null,
                analysisCompletedAt = null,
                analysisErrorMessage = null,
                needsReview = 0,
                itemsSubtotalYen = 0,
                adjustmentYen = 0,
                deletedAt = null,
                deleteReason = null,
                backupRevision = 0,
                createdAt = now,
                updatedAt = now,
            )
        )
        dao.upsertReceiptImage(
            ReceiptImageEntity(
                receiptId = receiptId,
                localUri = imageUri.toString(),
                byteSize = bytes,
                width = w,
                height = h,
                retentionUntil = Instant.parse(now).plus(40, ChronoUnit.DAYS).toString(),
                deletedAt = null,
            )
        )

        Log.d(TAG, "savePendingReceipt done receiptId=$receiptId bytes=$bytes w=$w h=$h")
        receiptId
    }

    data class ManualReceiptItemInput(
        val itemName: String,
        val quantity: Int,
        /** 行合計（数量込みの合計金額） */
        val lineTotalYen: Long,
        val categoryMajor: String,
        val categoryMinor: String,
        val necessityScore: Int,
    )

    data class ManualReceiptInput(
        val receiptDatetime: String,
        val merchantName: String,
        val totalAmountYen: Long,
        val paymentMethod: String,
        val paymentServiceName: String?,
        val items: List<ManualReceiptItemInput>,
    )

    data class ReceiptEditItemInput(
        val itemId: String?,
        val itemName: String,
        val quantity: Int,
        val lineTotalYen: Long,
        val categoryMajor: String,
        val categoryMinor: String,
        val necessityScore: Int,
        val confidence: Double,
    )

    data class ReceiptEditInput(
        val receiptId: String,
        val receiptDatetime: String,
        val merchantName: String,
        val totalAmountYen: Long,
        val paymentMethod: String,
        val paymentServiceName: String?,
        val items: List<ReceiptEditItemInput>,
    )

    /**
     * Phase6: レシートなし手入力。
     * - gemini_results を作らない
     * - analysis_queue に入れない
     * - 保存後も analysisStatus=DONE / needsReview=0
     */
    suspend fun saveManualNoReceipt(input: ManualReceiptInput): Result<String> = withContext(Dispatchers.IO) {
        if (input.items.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("明細は1件以上必要です"))
        }
        if (input.totalAmountYen < 0) {
            return@withContext Result.failure(IllegalArgumentException("合計金額が不正です"))
        }

        val sum = input.items.sumOf { it.lineTotalYen }
        if (sum != input.totalAmountYen) {
            return@withContext Result.failure(
                IllegalArgumentException("明細合計と合計金額が一致しません（明細=$sum 合計=${input.totalAmountYen}）"),
            )
        }

        val now = Instant.now().toString()
        val receiptId = UUID.randomUUID().toString()
        val receipt =
            ReceiptEntity(
                receiptId = receiptId,
                inputKind = "MANUAL_NO_RECEIPT",
                capturedAt = now, // 要件: 保存ボタン押下時の Instant
                receiptDatetime = input.receiptDatetime,
                analysisStatus = "DONE",
                merchantName = input.merchantName,
                totalAmountYen = input.totalAmountYen,
                paymentMethod = input.paymentMethod,
                paymentServiceName = input.paymentServiceName,
                analysisStartedAt = null,
                analysisCompletedAt = null,
                analysisErrorMessage = null,
                needsReview = 0,
                itemsSubtotalYen = sum,
                adjustmentYen = 0,
                deletedAt = null,
                deleteReason = null,
                backupRevision = 0,
                createdAt = now,
                updatedAt = now,
            )

        val items =
            input.items.mapIndexed { idx, it ->
                ReceiptItemEntity(
                    itemId = UUID.randomUUID().toString(),
                    receiptId = receiptId,
                    lineIndex = idx,
                    itemName = it.itemName,
                    quantity = it.quantity,
                    lineTotalYen = it.lineTotalYen,
                    categoryMajor = it.categoryMajor,
                    categoryMinor = it.categoryMinor,
                    necessityScore = it.necessityScore,
                    confidence = 1.0,
                    isAdjustment = 0,
                )
            }

        dao.replaceReceiptAndItems(receipt, items)
        Result.success(receiptId)
    }

    suspend fun enqueueAnalysis(receiptId: String): Boolean = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val enqueued = dao.enqueueOnce(receiptId = receiptId, queuedAt = now)
        Log.d(TAG, "enqueueAnalysis receiptId=$receiptId enqueued=$enqueued")
        if (enqueued) {
            scheduleAnalysisWork()
        }
        enqueued
    }

    fun scheduleAnalysisWork() {
        Log.d(TAG, "scheduleAnalysisWork enqueueUniqueWork($UNIQUE_WORK_NAME)")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
    }

    suspend fun listReceipts() = withContext(Dispatchers.IO) {
        dao.listReceipts()
    }

    suspend fun listReceiptRowsForMonth(yearMonth: String) = withContext(Dispatchers.IO) {
        if (yearMonth.isEmpty()) {
            dao.listReceiptRowsAllPeriods()
        } else {
            dao.listReceiptRowsFiltered(yearMonth)
        }
    }

    suspend fun getReceiptOrNull(receiptId: String) = withContext(Dispatchers.IO) {
        dao.getReceiptOrNull(receiptId)
    }

    suspend fun listReceiptItems(receiptId: String) = withContext(Dispatchers.IO) {
        dao.listReceiptItems(receiptId)
    }

    suspend fun listVisibleReceiptItems(receiptId: String) = withContext(Dispatchers.IO) {
        dao.listReceiptItems(receiptId).filter { it.isAdjustment == 0 }
    }

    suspend fun listNonAdjustmentItemsInMonth(yearMonth: String) = withContext(Dispatchers.IO) {
        dao.listNonAdjustmentItemsInMonth(yearMonth)
    }

    data class MonthAnalysisSummary(
        val yearMonth: String,
        val mandatoryYen: Long,
        val discretionaryYen: Long,
        val mandatoryLineCount: Int,
        val discretionaryLineCount: Int,
    )

    suspend fun monthAnalysisSummary(yearMonth: String): MonthAnalysisSummary = withContext(Dispatchers.IO) {
        val items = dao.listNonAdjustmentItemsInMonth(yearMonth)
        var mandatoryYen = 0L
        var discretionaryYen = 0L
        var mandatoryLineCount = 0
        var discretionaryLineCount = 0
        for (it in items) {
            val yen = it.lineTotalYen
            if (it.necessityScore >= 50) {
                if (yen > 0) mandatoryYen += yen
                mandatoryLineCount++
            } else {
                if (yen > 0) discretionaryYen += yen
                discretionaryLineCount++
            }
        }
        MonthAnalysisSummary(
            yearMonth = yearMonth,
            mandatoryYen = mandatoryYen,
            discretionaryYen = discretionaryYen,
            mandatoryLineCount = mandatoryLineCount,
            discretionaryLineCount = discretionaryLineCount,
        )
    }

    fun sortWasteCandidates(items: List<ReceiptItemEntity>, byAmount: Boolean): List<ReceiptItemEntity> {
        return if (byAmount) {
            items.sortedWith(compareByDescending<ReceiptItemEntity> { it.lineTotalYen }.thenByDescending { it.necessityScore })
        } else {
            items.sortedWith(
                compareByDescending<ReceiptItemEntity> { (100 - it.necessityScore) * it.lineTotalYen }
                    .thenByDescending { it.lineTotalYen },
            )
        }
    }

    suspend fun softDeleteReceipt(receiptId: String, deleteReason: String) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val existing = dao.getReceiptOrNull(receiptId) ?: return@withContext
        dao.deleteQueueForReceipt(receiptId)
        dao.upsertReceipt(
            existing.copy(
                deletedAt = now,
                deleteReason = deleteReason,
                updatedAt = now,
            ),
        )
    }

    /**
     * 修正完了: 必須充足時に needsReview 解除し DONE へ（要件 Phase3）。
     */
    suspend fun applyReceiptReview(
        receipt: ReceiptEntity,
        items: List<ReceiptItemEntity>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (receipt.deletedAt != null) {
            return@withContext Result.failure(IllegalStateException("削除済みのレシートは保存できません"))
        }
        if (receipt.analysisStatus == "PENDING" || receipt.analysisStatus == "RUNNING") {
            return@withContext Result.failure(IllegalStateException("解析完了前に確定できません"))
        }
        if (!ReceiptRequiredFields.isSatisfiedForReviewComplete(receipt, items)) {
            return@withContext Result.failure(IllegalStateException("必須項目が不足しています"))
        }
        val now = Instant.now().toString()
        val cleared = receipt.copy(
            needsReview = 0,
            analysisStatus = when (receipt.analysisStatus) {
                "NEEDS_REVIEW", "FAILED" -> "DONE"
                else -> receipt.analysisStatus
            },
            analysisErrorMessage = null,
            updatedAt = now,
        )
        dao.replaceReceiptAndItems(cleared, items)
        Result.success(Unit)
    }

    /**
     * Phase 6.3: レシート修正画面からのフル編集保存。
     * 明細の再採番・調整行の自動付与を行い、必須充足時に DONE へ確定する。
     */
    suspend fun applyReceiptEdit(input: ReceiptEditInput): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = dao.getReceiptOrNull(input.receiptId)
            ?: return@withContext Result.failure(IllegalStateException("レシートが見つかりません"))
        if (existing.deletedAt != null) {
            return@withContext Result.failure(IllegalStateException("削除済みのレシートは保存できません"))
        }
        if (existing.analysisStatus == "PENDING" || existing.analysisStatus == "RUNNING") {
            return@withContext Result.failure(IllegalStateException("解析完了前に保存できません"))
        }
        if (input.items.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("明細は1行以上必要です"))
        }
        if (input.items.size > 30) {
            return@withContext Result.failure(IllegalArgumentException("明細は最大30行までです"))
        }
        val merchantTrimmed = input.merchantName.trim()
        if (merchantTrimmed.isBlank() || merchantTrimmed.length > 80) {
            return@withContext Result.failure(IllegalArgumentException("店名を1〜80文字で入力してください"))
        }
        if (input.totalAmountYen < 0) {
            return@withContext Result.failure(IllegalArgumentException("合計金額が不正です"))
        }
        if (!ReceiptRequiredFields.isReceiptDatetimeParseable(input.receiptDatetime)) {
            return@withContext Result.failure(IllegalArgumentException("取引日時が不正です"))
        }
        if (input.paymentMethod.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("支払手段を選択してください"))
        }

        input.items.forEachIndexed { idx, it ->
            if (it.itemName.isBlank() || it.itemName.length > 120) {
                return@withContext Result.failure(IllegalArgumentException("${idx + 1}行目: 商品名を1〜120文字で入力してください"))
            }
            if (it.quantity < 1) {
                return@withContext Result.failure(IllegalArgumentException("${idx + 1}行目: 数量は1以上で入力してください"))
            }
            if (it.lineTotalYen < 0) {
                return@withContext Result.failure(IllegalArgumentException("${idx + 1}行目: 行合計（円）を入力してください"))
            }
            if (!CategoryCatalog.isValidPair(it.categoryMajor, it.categoryMinor)) {
                return@withContext Result.failure(IllegalArgumentException("${idx + 1}行目: カテゴリが不正です"))
            }
        }

        val nonAdjustmentItems = input.items.mapIndexed { idx, it ->
            ReceiptItemEntity(
                itemId = it.itemId ?: "${input.receiptId}:edit:${UUID.randomUUID()}",
                receiptId = input.receiptId,
                lineIndex = idx,
                itemName = it.itemName.trim(),
                quantity = it.quantity,
                lineTotalYen = it.lineTotalYen,
                categoryMajor = it.categoryMajor,
                categoryMinor = it.categoryMinor,
                necessityScore = it.necessityScore.coerceIn(0, 100),
                confidence = it.confidence,
                isAdjustment = 0,
            )
        }
        val subtotal = nonAdjustmentItems.sumOf { it.lineTotalYen }
        val adjustment = input.totalAmountYen - subtotal
        val allItems = nonAdjustmentItems.toMutableList()
        if (adjustment != 0L) {
            val maxLineIndex = nonAdjustmentItems.maxOfOrNull { it.lineIndex } ?: -1
            allItems += ReceiptItemEntity(
                itemId = "${input.receiptId}:adjustment",
                receiptId = input.receiptId,
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

        val updatedReceipt = existing.copy(
            merchantName = merchantTrimmed,
            receiptDatetime = input.receiptDatetime.trim(),
            totalAmountYen = input.totalAmountYen,
            paymentMethod = input.paymentMethod,
            paymentServiceName = input.paymentServiceName?.trim()?.ifBlank { null },
            itemsSubtotalYen = subtotal,
            adjustmentYen = adjustment,
        )
        applyReceiptReview(updatedReceipt, allItems)
    }

    /** 一覧行の加重平均（DAO サブクエリと一致させるための再計算用・テスト等） */
    fun weightedNecessityForItems(items: List<ReceiptItemEntity>): Double? =
        NecessityUtils.weightedAverageScore(items)

    suspend fun getReceiptImage(receiptId: String) = withContext(Dispatchers.IO) {
        dao.getReceiptImage(receiptId)
    }

    suspend fun getLatestGeminiJsonOrNull(receiptId: String): String? = withContext(Dispatchers.IO) {
        dao.getLatestGeminiResultOrNull(receiptId)?.rawJson
    }

    suspend fun cleanupExpiredImages(now: Instant = Instant.now()): Int = withContext(Dispatchers.IO) {
        val expired = dao.listExpiredImages(now.toString())
        var deleted = 0
        for (img in expired) {
            val uri = runCatching { Uri.parse(img.localUri) }.getOrNull() ?: continue
            val file = uri.toFileOrNull() ?: continue
            if (file.exists()) {
                runCatching { file.delete() }
            }
            dao.markImageDeleted(img.receiptId, now.toString())
            deleted++
        }
        deleted
    }

    /**
     * プレビュー中にキャンセルされた下書き（PENDING）を削除する。
     * - DBから receipts / receipt_images を消す
     * - 内部ファイルを物理削除する（file:// のみ）
     */
    suspend fun deleteDraftReceipt(receiptId: String): Unit = withContext(Dispatchers.IO) {
        dao.deleteQueueForReceipt(receiptId)
        val img = dao.getReceiptImage(receiptId)
        if (img != null) {
            val uri = runCatching { Uri.parse(img.localUri) }.getOrNull()
            val file = uri?.toFileOrNull()
            if (file != null && file.exists()) {
                runCatching { file.delete() }
            }
        }
        dao.deleteReceiptImage(receiptId)
        dao.deleteReceipt(receiptId)
    }

    /**
     * Phase6: 証拠画像の解析失敗時に「手入力へ切り替え」する。
     * - inputKind を MANUAL_NO_RECEIPT に変更
     * - backupRevision を +1
     * - 明細・画像・gemini_results は保持（この関数では触らない）
     */
    suspend fun switchEvidenceFailedToManual(receiptId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = dao.getReceiptOrNull(receiptId)
            ?: return@withContext Result.failure(IllegalStateException("レシートが見つかりません"))
        if (existing.inputKind != "EVIDENCE_IMAGE") {
            return@withContext Result.failure(IllegalStateException("証拠画像のレシートではありません"))
        }
        if (existing.analysisStatus != "FAILED") {
            return@withContext Result.failure(IllegalStateException("解析失敗のレシートではありません"))
        }
        val now = Instant.now().toString()
        dao.upsertReceipt(
            existing.copy(
                inputKind = "MANUAL_NO_RECEIPT",
                backupRevision = existing.backupRevision + 1,
                updatedAt = now,
            ),
        )
        Result.success(Unit)
    }

    private fun Uri.toFileOrNull(): File? {
        if (scheme != "file") return null
        val p = path ?: return null
        return File(p)
    }

    suspend fun queueInFlightCount(): Int = withContext(Dispatchers.IO) {
        dao.countQueueInFlight()
    }

    suspend fun latestQueueErrorOrNull(): String? = withContext(Dispatchers.IO) {
        dao.getLatestQueueErrorOrNull()
    }

    suspend fun listRecentAnalyzed(limit: Int = 30) = withContext(Dispatchers.IO) {
        dao.listRecentAnalyzed(limit)
    }

    suspend fun listNeedsReview(limit: Int = 30) = withContext(Dispatchers.IO) {
        dao.listNeedsReview(limit)
    }

    suspend fun listFailedForResend(limit: Int = 30) = withContext(Dispatchers.IO) {
        dao.listFailedForResend(limit)
    }

    suspend fun listNotificationHistory(limit: Int = NotificationHistory.DISPLAY_LIMIT) =
        withContext(Dispatchers.IO) {
            dao.listNotificationEvents(limit).map { event ->
                NotificationHistoryEntry(
                    event = event,
                    receipt = dao.getReceiptOrNull(event.receiptId),
                )
            }
        }

    sealed class ResendAnalysisResult {
        data object Success : ResendAnalysisResult()
        data object ReceiptNotFound : ResendAnalysisResult()
        data object NotFailed : ResendAnalysisResult()
        data object ApiKeyMissing : ResendAnalysisResult()
        data object ImageMissing : ResendAnalysisResult()
        data object AlreadyQueued : ResendAnalysisResult()
    }

    /**
     * Phase 6.2: 解析失敗レシートを手動で再送信する。
     * - キューを QUEUED に戻し attemptCount をリセット
     * - 自動リトライは行わない（AnalysisWorker 側で1回のみ実行）
     */
    suspend fun resendAnalysis(receiptId: String): ResendAnalysisResult = withContext(Dispatchers.IO) {
        val existing = dao.getReceiptOrNull(receiptId)
            ?: return@withContext ResendAnalysisResult.ReceiptNotFound
        if (existing.deletedAt != null) {
            return@withContext ResendAnalysisResult.ReceiptNotFound
        }
        if (existing.analysisStatus != "FAILED") {
            return@withContext ResendAnalysisResult.NotFailed
        }
        if (existing.inputKind == "MANUAL_NO_RECEIPT") {
            return@withContext ResendAnalysisResult.NotFailed
        }
        if (!GeminiApiKeyStore(context).hasKey()) {
            return@withContext ResendAnalysisResult.ApiKeyMissing
        }
        if (!isReceiptImageReadable(receiptId)) {
            return@withContext ResendAnalysisResult.ImageMissing
        }

        val queueEntry = dao.getQueueEntryForReceipt(receiptId)
        if (queueEntry != null && queueEntry.status in IN_FLIGHT_QUEUE_STATUSES) {
            return@withContext ResendAnalysisResult.AlreadyQueued
        }

        val now = Instant.now().toString()
        val queued = if (queueEntry != null) {
            dao.resetQueueForResend(receiptId, now) > 0
        } else {
            dao.enqueueOnce(receiptId, now)
        }
        if (!queued) {
            return@withContext ResendAnalysisResult.AlreadyQueued
        }

        dao.upsertReceipt(
            existing.copy(
                analysisStatus = "PENDING",
                analysisStartedAt = null,
                analysisCompletedAt = null,
                analysisErrorMessage = null,
                needsReview = 0,
                updatedAt = now,
            ),
        )
        scheduleAnalysisWork()
        Log.d(TAG, "resendAnalysis receiptId=$receiptId")
        ResendAnalysisResult.Success
    }

    fun necessityPolicyStore(): NecessityPolicyStore = NecessityPolicyStore(context)

    suspend fun addNecessityCorrection(
        phrase: String,
        direction: NecessityCorrectionDirection,
        sourceItemName: String? = null,
    ): NecessityCorrection = withContext(Dispatchers.IO) {
        necessityPolicyStore().addCorrection(phrase, direction, sourceItemName)
    }

    suspend fun removeNecessityCorrection(correctionId: String) = withContext(Dispatchers.IO) {
        necessityPolicyStore().removeCorrection(correctionId)
    }

    sealed class CompileNecessityPolicyResult {
        data class Success(val policy: CompiledNecessityPolicy) : CompileNecessityPolicyResult()
        data object ApiKeyMissing : CompileNecessityPolicyResult()
        data class Failure(val message: String) : CompileNecessityPolicyResult()
    }

    suspend fun compileNecessityPolicy(purposeId: NecessityPurposeId): CompileNecessityPolicyResult =
        withContext(Dispatchers.IO) {
            val apiKey = GeminiApiKeyStore(context).readKeyOrNull()
                ?: return@withContext CompileNecessityPolicyResult.ApiKeyMissing
            val store = necessityPolicyStore()
            store.setPurposeId(purposeId)
            val corrections = store.listCorrections()
            runCatching {
                val policy = NecessityPolicyCompiler().compileAndSave(store, apiKey, purposeId, corrections)
                CompileNecessityPolicyResult.Success(policy)
            }.getOrElse {
                CompileNecessityPolicyResult.Failure(it.message ?: it.javaClass.simpleName)
            }
        }

    fun scheduleNecessityRescore() {
        Log.d(TAG, "scheduleNecessityRescore enqueueUniqueWork(${NecessityRescoreWorker.UNIQUE_WORK_NAME})")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<NecessityRescoreWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                NecessityRescoreWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
    }

    private suspend fun isReceiptImageReadable(receiptId: String): Boolean {
        val img = dao.getReceiptImage(receiptId) ?: return false
        if (img.deletedAt != null) return false
        val uri = runCatching { Uri.parse(img.localUri) }.getOrNull() ?: return false
        val file = uri.toFileOrNull()
        if (file != null) return file.exists()
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { }
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "ReceiptRepo"
        private const val UNIQUE_WORK_NAME = "analysis-queue"
        private val IN_FLIGHT_QUEUE_STATUSES = setOf("QUEUED", "RUNNING")
    }
}

