package work.temp1209.kakeibo.data.ai

import android.util.Log
import org.json.JSONObject
import work.temp1209.kakeibo.data.prefs.AiProviderStore

/**
 * 設定されたプロバイダスロットを順に試し、リトライ可能なエラーで次へ切り替える。
 */
class AiRequestRouter(
    private val store: AiProviderStore,
    private val providers: Map<String, AiProvider> = AiProviderRegistry.defaultProviders(),
) {
    fun generateStrictJsonFromImage(
        jpegBytes: ByteArray,
        prompt: String,
        responseJsonSchema: JSONObject,
    ): AiRoutedResult = route { provider, apiKey, slot ->
        Log.d(TAG, "image via slot=${slot.label} provider=${slot.providerId}")
        provider.generateStrictJsonFromImage(apiKey, jpegBytes, prompt, responseJsonSchema)
    }

    fun generateStrictJsonFromText(
        prompt: String,
        responseJsonSchema: JSONObject,
    ): AiRoutedResult = route { provider, apiKey, slot ->
        Log.d(TAG, "text via slot=${slot.label} provider=${slot.providerId}")
        provider.generateStrictJsonFromText(apiKey, prompt, responseJsonSchema)
    }

    fun testSlot(slotId: String): String {
        val slot = store.getConfig().slots.find { it.slotId == slotId }
            ?: error("slot not found: $slotId")
        val apiKey = store.readApiKey(slotId)
            ?: error("API key missing for slot=$slotId")
        val provider = AiProviderRegistry.require(slot.providerId, providers)
        return provider.testConnectivity(apiKey)
    }

    fun testFirstEnabledSlot(): String {
        val slot = store.getConfig().orderedEnabledSlots().firstOrNull()
            ?: error(AiProviderStore.MISSING_KEY_USER_MESSAGE)
        return testSlot(slot.slotId)
    }

    private fun route(
        call: (AiProvider, String, ProviderSlot) -> String,
    ): AiRoutedResult {
        val slots = store.getConfig().orderedEnabledSlots()
        if (slots.isEmpty()) {
            throw IllegalStateException(AiProviderStore.MISSING_KEY_USER_MESSAGE)
        }
        Log.d(TAG, "route start order=${slots.joinToString(" → ") { it.label }}")

        var lastError: Throwable? = null
        var attempts = 0
        val eligibleCount = slots.count { !store.readApiKey(it.slotId).isNullOrBlank() && providers.containsKey(it.providerId) }
        for (slot in slots) {
            val apiKey = store.readApiKey(slot.slotId)
            if (apiKey.isNullOrBlank()) {
                Log.w(TAG, "skip slot=${slot.label} (no key)")
                continue
            }
            val provider = providers[slot.providerId]
            if (provider == null) {
                Log.w(TAG, "skip unsupported provider=${slot.providerId}")
                continue
            }
            attempts++
            try {
                val raw = call(provider, apiKey, slot)
                Log.d(
                    TAG,
                    "success slot=${slot.label} provider=${slot.providerId} attempt=$attempts/$eligibleCount",
                )
                return AiRoutedResult(
                    rawResponse = raw,
                    slotId = slot.slotId,
                    providerId = slot.providerId,
                    label = slot.label,
                    attemptIndex = attempts,
                    totalAttempts = eligibleCount.coerceAtLeast(attempts),
                )
            } catch (e: Exception) {
                lastError = e
                // 正常な通信完了以外はすべて次スロットへ（HTTP/タイムアウト/キー無効など）
                Log.w(TAG, "failover from slot=${slot.label}: ${e.message}")
                continue
            }
        }

        throw AllAiProvidersFailedException(
            attempts = attempts.coerceAtLeast(1),
            cause = lastError,
        )
    }

    companion object {
        private const val TAG = "AiRequestRouter"
    }
}
