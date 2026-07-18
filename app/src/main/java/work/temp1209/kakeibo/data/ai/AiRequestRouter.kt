package work.temp1209.kakeibo.data.ai

import android.util.Log
import org.json.JSONObject
import work.temp1209.kakeibo.data.prefs.AiProviderStore

/**
 * 設定されたプロバイダスロットを順に試し、通信失敗時は次スロットへ切り替える。
 *
 * 本番: [AiProviderStore] 経由のコンストラクタ。
 * テスト: [resolveSlots] を直接注入。
 */
class AiRequestRouter(
    private val resolveSlots: () -> List<Pair<ProviderSlot, String>>,
    private val providers: Map<String, AiProvider> = AiProviderRegistry.defaultProviders(),
    private val store: AiProviderStore? = null,
) {
    constructor(
        store: AiProviderStore,
        providers: Map<String, AiProvider> = AiProviderRegistry.defaultProviders(),
    ) : this(
        resolveSlots = {
            store.getConfig().orderedEnabledSlots().mapNotNull { slot ->
                val key = store.readApiKey(slot.slotId)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                slot to key
            }
        },
        providers = providers,
        store = store,
    )

    fun generateStrictJsonFromImage(
        jpegBytes: ByteArray,
        prompt: String,
        responseJsonSchema: JSONObject,
    ): AiRoutedResult = route { provider, apiKey, slot ->
        logD("image via slot=${slot.label} provider=${slot.providerId}")
        provider.generateStrictJsonFromImage(apiKey, jpegBytes, prompt, responseJsonSchema)
    }

    fun generateStrictJsonFromText(
        prompt: String,
        responseJsonSchema: JSONObject,
    ): AiRoutedResult = route { provider, apiKey, slot ->
        logD("text via slot=${slot.label} provider=${slot.providerId}")
        provider.generateStrictJsonFromText(apiKey, prompt, responseJsonSchema)
    }

    fun testSlot(slotId: String): String {
        val s = store ?: error("testSlot requires AiProviderStore-backed router")
        val slot = s.getConfig().slots.find { it.slotId == slotId }
            ?: error("slot not found: $slotId")
        val apiKey = s.readApiKey(slotId)
            ?: error("API key missing for slot=$slotId")
        val provider = AiProviderRegistry.require(slot.providerId, providers)
        return provider.testConnectivity(apiKey)
    }

    private fun route(
        call: (AiProvider, String, ProviderSlot) -> String,
    ): AiRoutedResult {
        val slotsWithKeys = resolveSlots()
        if (slotsWithKeys.isEmpty()) {
            throw IllegalStateException(AiProviderStore.MISSING_KEY_USER_MESSAGE)
        }
        logD("route start order=${slotsWithKeys.joinToString(" → ") { it.first.label }}")

        var lastError: Throwable? = null
        var attempts = 0
        val eligibleCount = slotsWithKeys.count { (slot, _) -> providers.containsKey(slot.providerId) }
        for ((slot, apiKey) in slotsWithKeys) {
            val provider = providers[slot.providerId]
            if (provider == null) {
                logW("skip unsupported provider=${slot.providerId}")
                continue
            }
            attempts++
            try {
                val raw = call(provider, apiKey, slot)
                logD(
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
                logW("failover from slot=${slot.label}: ${e.message}")
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

        /** JVM 単体テストでは android.util.Log が未モックのため握りつぶす。 */
        private fun logD(msg: String) {
            runCatching { Log.d(TAG, msg) }
        }

        private fun logW(msg: String) {
            runCatching { Log.w(TAG, msg) }
        }
    }
}
