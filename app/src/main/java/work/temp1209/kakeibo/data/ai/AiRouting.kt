package work.temp1209.kakeibo.data.ai

/**
 * 有効スロットがすべて失敗したとき。
 * [cause] は最後に失敗したスロットの例外（ユーザー向け詳細・レート制限判定用）。
 * [allCauses] は全スロットの失敗（一時的な失敗かの判定用。最後の1件だけだと、
 * 先に503だったスロットがあっても最後が400なら再試行不要と誤判定するため）。
 */
class AllAiProvidersFailedException(
    val attempts: Int,
    cause: Throwable?,
    val allCauses: List<Throwable> = listOfNotNull(cause),
) : Exception(
    buildMessage(attempts, cause),
    cause,
) {
    companion object {
        fun buildMessage(attempts: Int, cause: Throwable? = null): String {
            val base = if (attempts <= 1) {
                "APIの利用に失敗しました。設定で別のキーを追加するか、時間をおいて再試行してください。"
            } else {
                "すべての API（${attempts}件）で利用できませんでした。設定で別のキーを追加するか、時間をおいて再試行してください。"
            }
            val detail = cause?.message?.takeIf { it.isNotBlank() }?.take(200) ?: return base
            return "$base\n（詳細: $detail）"
        }
    }
}

data class AiRoutedResult(
    val rawResponse: String,
    val slotId: String,
    val providerId: String,
    val label: String,
    val attemptIndex: Int,
    val totalAttempts: Int,
)
