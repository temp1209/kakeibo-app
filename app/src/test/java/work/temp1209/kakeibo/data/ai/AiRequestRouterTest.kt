package work.temp1209.kakeibo.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import work.temp1209.kakeibo.data.gemini.GeminiUserMessages

class AiRequestRouterTest {
    @Test
    fun failsOverToSecondSlot_onAnyProviderException() {
        val provider = object : AiProvider {
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
        val router = AiRequestRouter(
            resolveSlots = {
                listOf(
                    ProviderSlot("s1", AiProviderId.GEMINI, "dummy", true) to "bad",
                    ProviderSlot("s2", AiProviderId.GEMINI, "main", true) to "good",
                )
            },
            providers = mapOf(AiProviderId.GEMINI to provider),
        )
        val result = router.generateStrictJsonFromText("p", JSONObject())
        assertEquals("s2", result.slotId)
        assertEquals(2, result.attemptIndex)
        assertTrue(result.rawResponse.contains("good"))
    }

    @Test
    fun allFail_throwsAllAiProvidersFailed_withCause() {
        val provider = object : AiProvider {
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
        val router = AiRequestRouter(
            resolveSlots = {
                listOf(
                    ProviderSlot("s1", AiProviderId.GEMINI, "a", true) to "k1",
                    ProviderSlot("s2", AiProviderId.GEMINI, "b", true) to "k2",
                )
            },
            providers = mapOf(AiProviderId.GEMINI to provider),
        )
        try {
            router.generateStrictJsonFromText("p", JSONObject())
            error("expected throw")
        } catch (e: AllAiProvidersFailedException) {
            assertEquals(2, e.attempts)
            assertTrue(GeminiUserMessages.isRateLimited(e))
            val msg = GeminiUserMessages.userFacingError(e, GeminiUserMessages.Operation.RECEIPT_ANALYSIS)
            assertTrue(msg.contains("利用上限"))
        }
    }

    @Test
    fun orderedEnabledSlots_includesSlotsMissingFromOrderList() {
        val config = AiProviderConfig(
            slots = listOf(
                ProviderSlot("a", AiProviderId.GEMINI, "A", true),
                ProviderSlot("b", AiProviderId.GEMINI, "B", true),
            ),
            orderedSlotIds = listOf("b"), // a is missing from order
        )
        assertEquals(listOf("b", "a"), config.orderedEnabledSlots().map { it.slotId })
    }
}
