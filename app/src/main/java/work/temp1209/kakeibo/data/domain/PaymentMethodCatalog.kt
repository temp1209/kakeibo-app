package work.temp1209.kakeibo.data.domain

object PaymentMethodCatalog {
    val codesOrdered: List<String> = listOf(
        "CASH",
        "CREDIT_CARD",
        "DEBIT_CARD",
        "TRANSPORT_IC",
        "E_MONEY",
        "QR_PAYMENT",
        "BANK_TRANSFER_OR_DEBIT",
        "UNKNOWN",
    )

    private val labels: Map<String, String> = mapOf(
        "CASH" to "現金",
        "CREDIT_CARD" to "クレジットカード",
        "DEBIT_CARD" to "デビットカード",
        "TRANSPORT_IC" to "交通系IC",
        "E_MONEY" to "電子マネー",
        "QR_PAYMENT" to "QR決済",
        "BANK_TRANSFER_OR_DEBIT" to "振込/銀行引落",
        "UNKNOWN" to "不明",
    )

    fun label(code: String?): String {
        if (code.isNullOrBlank()) return "未設定"
        return labels[code] ?: code
    }
}
