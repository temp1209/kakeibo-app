package work.temp1209.kakeibo.data.gemini

object GeminiUserMessages {

    enum class Operation {
        RECEIPT_ANALYSIS,
        POLICY_COMPILE,
        NECESSITY_RESCORE,
        CONNECTIVITY_TEST,
    }

    fun userFacingError(throwable: Throwable, operation: Operation = Operation.RECEIPT_ANALYSIS): String {
        val msg = throwable.message.orEmpty()
        return when {
            msg.contains("timeout", ignoreCase = true) ->
                "通信がタイムアウトしました。${retryHint(operation)}"
            isRateLimited(msg) ->
                "APIの利用上限に達した可能性があります。${retryHint(operation)}"
            operation == Operation.RECEIPT_ANALYSIS &&
                (msg.contains("receipt image missing", ignoreCase = true) ||
                    msg.contains("failed to read image", ignoreCase = true)) ->
                "画像ファイルが見つかりません。再撮影するか削除してください。"
            msg.contains("no candidates", ignoreCase = true) ||
                msg.contains("empty response", ignoreCase = true) ->
                "AIから有効な応答がありませんでした。${retryHint(operation)}"
            operation == Operation.RECEIPT_ANALYSIS ->
                "解析に失敗しました: ${throwable.message ?: "不明なエラー"}"
            operation == Operation.POLICY_COMPILE ->
                "方針のコンパイルに失敗しました: ${throwable.message ?: "不明なエラー"}"
            operation == Operation.NECESSITY_RESCORE ->
                "必須度の再計算に失敗しました: ${throwable.message ?: "不明なエラー"}"
            operation == Operation.CONNECTIVITY_TEST ->
                "疎通NG: ${throwable.message ?: "不明なエラー"}"
            else -> throwable.message ?: "不明なエラー"
        }
    }

    fun policyCompileFailure(throwable: Throwable): String {
        val base = userFacingError(throwable, Operation.POLICY_COMPILE)
        return if (isRateLimited(throwable)) {
            "$base\n前回の方針と訂正例はそのままです。"
        } else {
            base
        }
    }

    fun necessityRescoreFailure(throwable: Throwable, partialUpdatedCount: Int): String {
        val base = userFacingError(throwable, Operation.NECESSITY_RESCORE)
        return if (partialUpdatedCount > 0) {
            "$base\n${partialUpdatedCount}件は再計算済みです。"
        } else {
            base
        }
    }

    fun isRateLimited(throwable: Throwable): Boolean = isRateLimited(throwable.message.orEmpty())

    fun isRateLimited(message: String): Boolean =
        message.contains("429") ||
            message.contains("quota", ignoreCase = true) ||
            message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            message.contains("rate limit", ignoreCase = true) ||
            message.contains("Too Many Requests", ignoreCase = true)

    private fun retryHint(operation: Operation): String = when (operation) {
        Operation.RECEIPT_ANALYSIS -> "しばらく待ってから再送信してください。"
        Operation.POLICY_COMPILE -> "時間をおいて再度コンパイルしてください。"
        Operation.NECESSITY_RESCORE -> "時間をおいて再度お試しください。"
        Operation.CONNECTIVITY_TEST -> "しばらく待ってから再試行してください。"
    }
}
