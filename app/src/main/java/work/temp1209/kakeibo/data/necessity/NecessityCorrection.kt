package work.temp1209.kakeibo.data.necessity

data class NecessityCorrection(
    val correctionId: String,
    val phrase: String,
    val scoreBefore: Int,
    val scoreAfter: Int,
    val receiptId: String? = null,
    val merchantName: String? = null,
    val sourceItemName: String? = null,
    val createdAt: String,
)
