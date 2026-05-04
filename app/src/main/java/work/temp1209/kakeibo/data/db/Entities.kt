package work.temp1209.kakeibo.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val receiptId: String,
    val inputKind: String = "RECEIPT_CAMERA",
    val capturedAt: String,
    val receiptDatetime: String?,
    val analysisStatus: String,
    val merchantName: String?,
    val totalAmountYen: Long?,
    val paymentMethod: String?,
    val paymentServiceName: String?,
    val analysisStartedAt: String?,
    val analysisCompletedAt: String?,
    val analysisErrorMessage: String?,
    val needsReview: Int,
    val itemsSubtotalYen: Long,
    val adjustmentYen: Long,
    val deletedAt: String?,
    val deleteReason: String?,
    val backupRevision: Int,
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

@Entity(
    tableName = "receipt_items",
    indices = [
        Index(value = ["receiptId"]),
        Index(value = ["receiptId", "lineIndex"], unique = true),
        Index(value = ["categoryMajor", "categoryMinor"]),
        Index(value = ["necessityScore"]),
    ],
)
data class ReceiptItemEntity(
    @PrimaryKey val itemId: String,
    val receiptId: String,
    val lineIndex: Int,
    val itemName: String,
    val quantity: Int,
    val lineTotalYen: Long,
    val categoryMajor: String,
    val categoryMinor: String,
    val necessityScore: Int,
    val confidence: Double,
    val isAdjustment: Int,
)

@Entity(
    tableName = "gemini_results",
    indices = [
        Index(value = ["receiptId"]),
    ],
)
data class GeminiResultEntity(
    @PrimaryKey val resultId: String,
    val receiptId: String,
    val schemaVersion: String,
    val model: String,
    val rawJson: String,
    val createdAt: String,
)

@Entity(
    tableName = "analysis_queue",
    indices = [
        Index(value = ["status", "queuedAt"]),
        Index(value = ["receiptId"], unique = true),
    ],
)
data class AnalysisQueueEntity(
    @PrimaryKey val queueId: String,
    val receiptId: String,
    val status: String,
    val attemptCount: Int,
    val lastError: String?,
    val queuedAt: String,
    val startedAt: String?,
    val finishedAt: String?,
)

