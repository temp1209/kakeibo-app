package work.temp1209.kakeibo.data.ai

object AiProviderId {
    const val GEMINI = "GEMINI"

    fun displayName(providerId: String): String = when (providerId) {
        GEMINI -> "Gemini"
        else -> providerId
    }
}
