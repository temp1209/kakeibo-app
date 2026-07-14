package work.temp1209.kakeibo.data.ai

object AiProviderRegistry {
    fun defaultProviders(): Map<String, AiProvider> = mapOf(
        AiProviderId.GEMINI to GeminiAiProvider(),
    )

    fun require(providerId: String, providers: Map<String, AiProvider> = defaultProviders()): AiProvider =
        providers[providerId]
            ?: error("unsupported AI provider: $providerId")
}
