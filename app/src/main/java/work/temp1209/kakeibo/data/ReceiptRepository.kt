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
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptImageEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.db.ReceiptListRow
import work.temp1209.kakeibo.data.domain.NecessityUtils
import work.temp1209.kakeibo.data.domain.ReceiptRequiredFields
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ReceiptRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).receiptDao()

    suspend fun savePendingReceipt(imageUri: Uri): String = withContext(Dispatchers.IO) {
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
        dao.listReceiptRowsFiltered(yearMonth)
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
        dao.upsertReceiptAndItems(cleared, items)
        Result.success(Unit)
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

    companion object {
        private const val TAG = "ReceiptRepo"
        private const val UNIQUE_WORK_NAME = "analysis-queue"
    }
}

