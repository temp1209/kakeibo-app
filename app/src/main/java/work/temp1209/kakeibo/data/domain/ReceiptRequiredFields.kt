package work.temp1209.kakeibo.data.domain

import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity

/**
 * 要件の必須フィールド（取引日時・合計・店名・カテゴリ大+小）と支払手段の充足判定。
 */
object ReceiptRequiredFields {
    const val CONFIDENCE_THRESHOLD = 0.7

    fun missingReceiptHeaders(receipt: ReceiptEntity): List<String> {
        val m = mutableListOf<String>()
        if (receipt.receiptDatetime.isNullOrBlank()) m += "取引日時"
        if (receipt.merchantName.isNullOrBlank()) m += "店名"
        val total = receipt.totalAmountYen
        if (total == null || total < 0) m += "合計金額"
        return m
    }

    /** 非調整行ごとのカテゴリ不備（大・小の欠落または不正ペア） */
    fun itemsWithInvalidCategory(items: List<ReceiptItemEntity>): List<ReceiptItemEntity> =
        items.filter { it.isAdjustment == 0 }.filter {
            it.categoryMajor.isBlank() ||
                it.categoryMinor.isBlank() ||
                !CategoryCatalog.isValidPair(it.categoryMajor, it.categoryMinor)
        }

    fun paymentMissing(receipt: ReceiptEntity): Boolean =
        receipt.paymentMethod.isNullOrBlank()

    fun hasLowConfidenceItem(items: List<ReceiptItemEntity>): Boolean =
        items.any { it.isAdjustment == 0 && it.confidence < CONFIDENCE_THRESHOLD }

    fun isSatisfiedForReviewComplete(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): Boolean {
        if (missingReceiptHeaders(receipt).isNotEmpty()) return false
        if (paymentMissing(receipt)) return false
        if (itemsWithInvalidCategory(items).isNotEmpty()) return false
        return true
    }
}
