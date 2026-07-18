package work.temp1209.kakeibo.ui.common

fun analysisErrorSummary(message: String?): String? {
    val normalized = message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (
        normalized.contains("429") ||
        normalized.contains("レート制限") ||
        normalized.contains("利用上限")
    ) {
        return "利用上限に達しました"
    }

    val firstLine = normalized.lineSequence().first().trim()
    val firstSentence = firstLine.substringBefore("。").trim()
    val summary = firstSentence.ifEmpty { firstLine }
    return if (summary.length <= 60) summary else summary.take(59) + "…"
}
