package work.temp1209.kakeibo.data.ai

/** 有効スロットがすべて失敗したとき。 */
class AllAiProvidersFailedException(
    val attempts: Int,
    cause: Throwable?,
) : Exception(
    buildMessage(attempts),
    cause,
) {
    companion object {
        fun buildMessage(attempts: Int): String =
            if (attempts <= 1) {
                "APIの利用に失敗しました。設定で別のキーを追加するか、時間をおいて再試行してください。"
            } else {
                "すべての API（${attempts}件）で利用できませんでした。設定で別のキーを追加するか、時間をおいて再試行してください。"
            }
    }
}

data class AiRoutedResult(
    val rawResponse: String,
    val slotId: String,
    val providerId: String,
    val label: String,
)
