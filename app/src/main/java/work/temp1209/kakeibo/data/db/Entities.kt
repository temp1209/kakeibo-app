package work.temp1209.kakeibo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val receiptId: String,
    val capturedAt: String,
    val receiptDatetime: String?,
    val analysisStatus: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "receipt_images", primaryKeys = ["receiptId"])
data class ReceiptImageEntity(
    val receiptId: String,
    val localUri: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val retentionUntil: String,
    val deletedAt: String?,
)

