package work.temp1209.kakeibo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceipt(receipt: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceiptImage(image: ReceiptImageEntity)

    @Query("SELECT * FROM receipts ORDER BY COALESCE(receiptDatetime, capturedAt) DESC")
    suspend fun listReceipts(): List<ReceiptEntity>

    @Query("SELECT * FROM receipt_images WHERE receiptId = :receiptId LIMIT 1")
    suspend fun getReceiptImage(receiptId: String): ReceiptImageEntity?

    @Query("SELECT * FROM receipt_images WHERE retentionUntil < :now AND deletedAt IS NULL")
    suspend fun listExpiredImages(now: String): List<ReceiptImageEntity>

    @Query("UPDATE receipt_images SET deletedAt = :deletedAt WHERE receiptId = :receiptId")
    suspend fun markImageDeleted(receiptId: String, deletedAt: String)
}

