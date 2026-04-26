package work.temp1209.kakeibo.data.analysis.model

data class GeminiReceiptResponse(
    val schemaVersion: String,
    val receipt: ReceiptHeader,
    val items: List<ReceiptItem>,
    val warnings: List<String> = emptyList(),
)

data class ReceiptHeader(
    val receiptDatetime: String,
    val capturedAt: String? = null,
    val merchantName: String,
    val totalAmountYen: Long,
    val paymentMethod: String? = null,
    val paymentServiceName: String? = null,
)

data class ReceiptItem(
    val lineIndex: Int,
    val itemName: String,
    val quantity: Int,
    val lineTotalYen: Long,
    val categoryMajor: String,
    val categoryMinor: String,
    val necessityScore: Int,
    val confidence: Double,
)

data class ReviewFlags(
    val needsReview: Boolean,
    val reasons: List<String>,
)

