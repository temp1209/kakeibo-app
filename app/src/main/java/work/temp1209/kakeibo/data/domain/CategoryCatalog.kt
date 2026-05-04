package work.temp1209.kakeibo.data.domain

/**
 * 要件の大/小カテゴリ固定セット（Gemini スキーマと整合）。
 */
object CategoryCatalog {
    val majorsOrdered: List<Pair<String, String>> = listOf(
        "FOOD" to "食費",
        "DAILY_GOODS" to "日用品",
        "TRANSPORT" to "交通",
        "HOUSING" to "住居",
        "UTILITIES" to "光熱",
        "COMMUNICATION" to "通信",
        "MEDICAL" to "医療",
        "EDUCATION" to "教育",
        "SOCIAL" to "交際",
        "ENTERTAINMENT" to "娯楽",
        "CLOTHING" to "衣服",
        "OTHER" to "その他",
    )

    private val minorsByMajor: Map<String, Set<String>> = mapOf(
        "FOOD" to setOf("スーパー", "外食", "その他"),
        "DAILY_GOODS" to setOf("衛生用品", "雑貨（小物）", "家具家電", "その他"),
        "TRANSPORT" to setOf("電車/バス", "定期券", "その他"),
        "HOUSING" to setOf("家賃/住宅ローン", "家具（大物）", "その他"),
        "UTILITIES" to setOf("電気", "ガス", "水道", "その他"),
        "COMMUNICATION" to setOf("携帯", "インターネット", "サブスク（通信系）", "その他"),
        "MEDICAL" to setOf("病院", "薬", "健康/検診", "その他"),
        "EDUCATION" to setOf("教材", "その他"),
        "SOCIAL" to setOf("交際費", "飲み会", "贈り物", "冠婚葬祭", "その他"),
        "ENTERTAINMENT" to setOf("趣味", "旅行", "イベント", "ゲーム（買い切り）", "ゲーム（課金）", "その他"),
        "CLOTHING" to setOf("衣類", "靴/バッグ", "クリーニング", "その他"),
        "OTHER" to setOf("返済", "その他"),
    )

    fun minorsFor(major: String): List<String> =
        minorsByMajor[major]?.toList().orEmpty()

    fun isValidPair(major: String, minor: String): Boolean {
        val set = minorsByMajor[major] ?: return false
        return minor in set
    }

    fun majorLabel(code: String): String =
        majorsOrdered.firstOrNull { it.first == code }?.second ?: code
}
