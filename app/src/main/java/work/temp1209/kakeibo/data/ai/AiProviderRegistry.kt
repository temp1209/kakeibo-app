package work.temp1209.kakeibo.data.ai

object AiProviderRegistry {
    private val defaults: Map<String, AiProvider> by lazy {
        mapOf(AiProviderId.GEMINI to GeminiAiProvider())
    }

    fun defaultProviders(): Map<String, AiProvider> = defaults

    fun require(providerId: String, providers: Map<String, AiProvider> = defaultProviders()): AiProvider =
        providers[providerId]
            ?: error("unsupported AI provider: $providerId")
}
