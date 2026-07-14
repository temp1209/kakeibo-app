package work.temp1209.kakeibo.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class AiRequestRouterTest {
    @Test
    fun failsOverToSecondSlot_onAnyProviderException() {
        val failing = object : AiProvider {
            override val providerId = AiProviderId.GEMINI
            override fun testConnectivity(apiKey: String) = error("unused")
            override fun generateStrictJsonFromImage(
                apiKey: String,
                jpegBytes: ByteArray,
                prompt: String,
                responseJsonSchema: JSONObject,
            ) = error("unused")
            override fun generateStrictJsonFromText(
                apiKey: String,
                prompt: String,
                responseJsonSchema: JSONObject,
            ): String {
                if (apiKey == "bad") throw IllegalStateException("HTTP 400: API_KEY_INVALID")
                return """{"ok":true,"key":"$apiKey"}"""
            }
        }
        val result = routeText(
            slots = listOf(
                ProviderSlot("s1", AiProviderId.GEMINI, "main", true),
                ProviderSlot("s2", AiProviderId.GEMINI, "backup", true),
            ),
            keys = mapOf("s1" to "bad", "s2" to "good"),
            providers = mapOf(AiProviderId.GEMINI to failing),
            prompt = "p",
            schema = JSONObject(),
        )
        assertEquals("s2", result.slotId)
        assertTrue(result.rawResponse.contains("good"))
    }

    @Test
    fun failsOver_onArbitraryException() {
        val failing = object : AiProvider {
            override val providerId = AiProviderId.GEMINI
            override fun testConnectivity(apiKey: String) = error("unused")
            override fun generateStrictJsonFromImage(
                apiKey: String,
                jpegBytes: ByteArray,
                prompt: String,
                responseJsonSchema: JSONObject,
            ) = error("unused")
            override fun generateStrictJsonFromText(
                apiKey: String,
                prompt: String,
                responseJsonSchema: JSONObject,
            ): String {
                if (apiKey == "bad") throw IllegalStateException("something unexpected")
                return """{"ok":true}"""
            }
        }
        val result = routeText(
            slots = listOf(
                ProviderSlot("s1", AiProviderId.GEMINI, "a", true),
                ProviderSlot("s2", AiProviderId.GEMINI, "b", true),
            ),
            keys = mapOf("s1" to "bad", "s2" to "good"),
            providers = mapOf(AiProviderId.GEMINI to failing),
            prompt = "p",
            schema = JSONObject(),
        )
        assertEquals("s2", result.slotId)
    }

    @Test
    fun allFail_throwsAllAiProvidersFailed() {
        val failing = object : AiProvider {
            override val providerId = AiProviderId.GEMINI
            override fun testConnectivity(apiKey: String) = error("unused")
            override fun generateStrictJsonFromImage(
                apiKey: String,
                jpegBytes: ByteArray,
                prompt: String,
                responseJsonSchema: JSONObject,
            ) = error("unused")
            override fun generateStrictJsonFromText(
                apiKey: String,
                prompt: String,
                responseJsonSchema: JSONObject,
            ): String = throw IllegalStateException("HTTP 429: quota")
        }
        try {
            routeText(
                slots = listOf(
                    ProviderSlot("s1", AiProviderId.GEMINI, "a", true),
                    ProviderSlot("s2", AiProviderId.GEMINI, "b", true),
                ),
                keys = mapOf("s1" to "k1", "s2" to "k2"),
                providers = mapOf(AiProviderId.GEMINI to failing),
                prompt = "p",
                schema = JSONObject(),
            )
            error("expected throw")
        } catch (e: AllAiProvidersFailedException) {
            assertEquals(2, e.attempts)
        }
    }

    private fun routeText(
        slots: List<ProviderSlot>,
        keys: Map<String, String>,
        providers: Map<String, AiProvider>,
        prompt: String,
        schema: JSONObject,
    ): AiRoutedResult {
        var lastError: Throwable? = null
        var attempts = 0
        for (slot in slots.filter { it.enabled }) {
            val apiKey = keys[slot.slotId] ?: continue
            val provider = providers[slot.providerId] ?: continue
            attempts++
            try {
                val raw = provider.generateStrictJsonFromText(apiKey, prompt, schema)
                return AiRoutedResult(raw, slot.slotId, slot.providerId, slot.label)
            } catch (e: Exception) {
                lastError = e
                continue
            }
        }
        throw AllAiProvidersFailedException(attempts.coerceAtLeast(1), lastError)
    }
}
