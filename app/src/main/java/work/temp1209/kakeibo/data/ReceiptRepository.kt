package work.temp1209.kakeibo.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.analysis.AnalysisWorker
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptImageEntity
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ReceiptRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).receiptDao()

    suspend fun savePendingReceipt(imageUri: Uri): String = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val receiptId = UUID.randomUUID().toString()

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

        receiptId
    }

    suspend fun enqueueAnalysis(receiptId: String): Boolean = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val enqueued = dao.enqueueOnce(receiptId = receiptId, queuedAt = now)
        if (enqueued) {
            scheduleAnalysisWork()
        }
        enqueued
    }

    fun scheduleAnalysisWork() {
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
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

    suspend fun getReceiptImage(receiptId: String) = withContext(Dispatchers.IO) {
        dao.getReceiptImage(receiptId)
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

    companion object {
        private const val UNIQUE_WORK_NAME = "analysis-queue"
    }
}

