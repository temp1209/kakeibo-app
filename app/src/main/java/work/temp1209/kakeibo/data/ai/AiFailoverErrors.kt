package work.temp1209.kakeibo.data.ai

import work.temp1209.kakeibo.data.gemini.GeminiUserMessages

/**
 * フェイルオーバーで次スロットへ進むべきエラーか判定する。
 * パース失敗・スキーマ不一致は対象外（呼び出し側で別扱い）。
 *
 * 注: Gemini は無効 API キーを **HTTP 400**（API_KEY_INVALID 等）で返すことが多い。
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
        if (isApiKeyOrAuthError(msg)) return true
        // GeminiClient の HTTP 失敗はキー切替で救えることが多い（パース失敗は HTTP 200 後に発生）
        val httpCode = httpStatusCode(msg) ?: return false
        return httpCode in 400..599
    }

    fun isApiKeyOrAuthError(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("api_key_invalid") ||
            m.contains("api key not valid") ||
            m.contains("invalid api key") ||
            m.contains("api key expired") ||
            m.contains("permission_denied") ||
            m.contains("unauthenticated")
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
