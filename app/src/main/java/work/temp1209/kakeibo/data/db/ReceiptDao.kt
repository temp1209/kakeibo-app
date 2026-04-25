package work.temp1209.kakeibo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceipt(receipt: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceiptImage(image: ReceiptImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceiptItems(items: List<ReceiptItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeminiResult(result: GeminiResultEntity)

    @Query("SELECT * FROM gemini_results WHERE receiptId = :receiptId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestGeminiResultOrNull(receiptId: String): GeminiResultEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQueue(entry: AnalysisQueueEntity)

    @Query("SELECT * FROM receipts ORDER BY COALESCE(receiptDatetime, capturedAt) DESC")
    suspend fun listReceipts(): List<ReceiptEntity>

    @Query("SELECT * FROM receipts WHERE receiptId = :receiptId LIMIT 1")
    suspend fun getReceiptOrNull(receiptId: String): ReceiptEntity?

    @Query("SELECT * FROM receipt_images WHERE receiptId = :receiptId LIMIT 1")
    suspend fun getReceiptImage(receiptId: String): ReceiptImageEntity?

    @Query("SELECT * FROM analysis_queue WHERE status = 'QUEUED' ORDER BY queuedAt ASC LIMIT 1")
    suspend fun getNextQueuedOrNull(): AnalysisQueueEntity?

    @Query("UPDATE analysis_queue SET status = :status, startedAt = :startedAt WHERE queueId = :queueId")
    suspend fun markQueueRunning(queueId: String, status: String = "RUNNING", startedAt: String)

    @Query("UPDATE analysis_queue SET status = :status, finishedAt = :finishedAt, lastError = :lastError, attemptCount = :attemptCount WHERE queueId = :queueId")
    suspend fun finishQueue(queueId: String, status: String, finishedAt: String, lastError: String?, attemptCount: Int)

    @Query("UPDATE analysis_queue SET status = 'QUEUED', attemptCount = :attemptCount, lastError = :lastError, startedAt = NULL, finishedAt = NULL WHERE queueId = :queueId")
    suspend fun requeue(queueId: String, attemptCount: Int, lastError: String?)

    @Query("SELECT COUNT(*) FROM analysis_queue WHERE status IN ('QUEUED','RUNNING')")
    suspend fun countQueueInFlight(): Int

    @Query("SELECT lastError FROM analysis_queue WHERE lastError IS NOT NULL ORDER BY queuedAt DESC LIMIT 1")
    suspend fun getLatestQueueErrorOrNull(): String?

    @Query("SELECT * FROM receipt_images WHERE retentionUntil < :now AND deletedAt IS NULL")
    suspend fun listExpiredImages(now: String): List<ReceiptImageEntity>

    @Query("UPDATE receipt_images SET deletedAt = :deletedAt WHERE receiptId = :receiptId")
    suspend fun markImageDeleted(receiptId: String, deletedAt: String)

    @Transaction
    suspend fun enqueueOnce(receiptId: String, queuedAt: String): Boolean {
        return try {
            insertQueue(
                AnalysisQueueEntity(
                    queueId = receiptId, // simplest stable id; unique index on receiptId prevents dup
                    receiptId = receiptId,
                    status = "QUEUED",
                    attemptCount = 0,
                    lastError = null,
                    queuedAt = queuedAt,
                    startedAt = null,
                    finishedAt = null,
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}

