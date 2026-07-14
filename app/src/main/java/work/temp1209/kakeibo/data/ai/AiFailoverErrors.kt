package work.temp1209.kakeibo.data.ai

import work.temp1209.kakeibo.data.gemini.GeminiUserMessages

/**
 * フェイルオーバーで次スロットへ進むべきエラーか判定する。
 * パース失敗・スキーマ不一致は対象外（呼び出し側で別扱い）。
 */
object AiFailoverErrors {
    fun isFailoverable(throwable: Throwable): Boolean {
        if (throwable is java.net.SocketTimeoutException ||
            throwable is java.net.ConnectException ||
            throwable is java.net.UnknownHostException
        ) {
            return true
        }
        val msg = throwable.message.orEmpty()
        if (GeminiUserMessages.isRateLimited(msg)) return true
        if (msg.contains("timeout", ignoreCase = true)) return true
        if (msg.contains("timed out", ignoreCase = true)) return true
        if (msg.contains("SocketTimeout", ignoreCase = true)) return true
        if (msg.contains("ConnectException", ignoreCase = true)) return true
        if (msg.contains("UnknownHost", ignoreCase = true)) return true
        // HTTP status in IllegalStateException messages from GeminiClient ("HTTP 429: ...")
        val httpCode = httpStatusCode(msg) ?: return false
        return when (httpCode) {
            in 500..599 -> true
            401, 403 -> true
            else -> false
        }
    }

    fun httpStatusCode(message: String): Int? {
        val match = Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE).find(message)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}

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
