package work.temp1209.kakeibo.data.backup

import com.google.gson.annotations.SerializedName
import work.temp1209.kakeibo.data.necessity.CompiledNecessityPolicy
import work.temp1209.kakeibo.data.necessity.NecessityCorrection

data class KakeiboBackupFile(
    @SerializedName("backupSchemaVersion") val backupSchemaVersion: String = "1.3",
    @SerializedName("exportType") val exportType: String,
    @SerializedName("exportedAt") val exportedAt: String,
    @SerializedName("rangeStart") val rangeStart: String,
    @SerializedName("rangeEnd") val rangeEnd: String,
    @SerializedName("app") val app: BackupAppInfo,
    @SerializedName("data") val data: BackupDataPayload,
    @SerializedName("necessityPolicy") val necessityPolicy: NecessityPolicyBackupDto? = null,
    @SerializedName("budget") val budget: BudgetBackupDto? = null,
)

data class BackupAppInfo(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Long,
)

data class BackupDataPayload(
    @SerializedName("receipts") val receipts: List<ReceiptBackupDto>,
    @SerializedName("receiptItems") val receiptItems: List<ReceiptItemBackupDto>,
)

data class ReceiptBackupDto(
    @SerializedName("receiptId") val receiptId: String,
    @SerializedName("inputKind") val inputKind: String? = null,
    @SerializedName("receiptDatetime") val receiptDatetime: String?,
    @SerializedName("capturedAt") val capturedAt: String?,
    @SerializedName("merchantName") val merchantName: String?,
    @SerializedName("totalAmountYen") val totalAmountYen: Long?,
    @SerializedName("paymentMethod") val paymentMethod: String?,
    @SerializedName("paymentServiceName") val paymentServiceName: String?,
    @SerializedName("analysisStatus") val analysisStatus: String,
    @SerializedName("analysisErrorMessage") val analysisErrorMessage: String? = null,
    @SerializedName("needsReview") val needsReview: Boolean,
    @SerializedName("itemsSubtotalYen") val itemsSubtotalYen: Long,
    @SerializedName("adjustmentYen") val adjustmentYen: Long,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("deletedAt") val deletedAt: String?,
    @SerializedName("deleteReason") val deleteReason: String?,
    @SerializedName("backupRevision") val backupRevision: Int,
)

data class ReceiptItemBackupDto(
    @SerializedName("itemId") val itemId: String,
    @SerializedName("receiptId") val receiptId: String,
    @SerializedName("lineIndex") val lineIndex: Int,
    @SerializedName("itemName") val itemName: String,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("lineTotalYen") val lineTotalYen: Long,
    @SerializedName("categoryMajor") val categoryMajor: String,
    @SerializedName("categoryMinor") val categoryMinor: String,
    @SerializedName("necessityScore") val necessityScore: Int,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("isAdjustment") val isAdjustment: Boolean,
)

object BackupExportTypes {
    const val CURRENT_MONTH = "CURRENT_MONTH"
    const val ARCHIVE_MONTH = "ARCHIVE_MONTH"
    const val FULL_SNAPSHOT = "FULL_SNAPSHOT"
}

data class NecessityPolicyBackupDto(
    @SerializedName("purposeId") val purposeId: String,
    @SerializedName("corrections") val corrections: List<NecessityCorrection>,
    @SerializedName("compiledPolicy") val compiledPolicy: CompiledNecessityPolicy?,
)

data class BudgetBackupDto(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("monthlyBudgetYen") val monthlyBudgetYen: Long,
    @SerializedName("aggregateMode") val aggregateMode: String,
)
