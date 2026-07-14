package work.temp1209.kakeibo.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class AiFailoverErrorsTest {
    @Test
    fun rateLimit_isFailoverable() {
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 429: quota")))
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("RESOURCE_EXHAUSTED")))
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("Too Many Requests")))
    }

    @Test
    fun serverError_andAuth_areFailoverable() {
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 500: boom")))
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 503")))
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 401: invalid")))
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 403: forbidden")))
    }

    @Test
    fun timeout_isFailoverable() {
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("timeout")))
        assertTrue(AiFailoverErrors.isFailoverable(java.net.SocketTimeoutException("read timed out")))
    }

    @Test
    fun clientHttpErrors_areFailoverable_includingInvalidKey400() {
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 400: API_KEY_INVALID")))
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 400: API key not valid")))
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 401: unauthorized")))
        assertTrue(AiFailoverErrors.isFailoverable(IllegalStateException("HTTP 403: forbidden")))
    }

    @Test
    fun parseErrors_withoutHttp_areNotFailoverable() {
        assertFalse(AiFailoverErrors.isFailoverable(IllegalStateException("unsupported schemaVersion=2.0")))
        assertFalse(AiFailoverErrors.isFailoverable(IllegalArgumentException("parse failed")))
    }

    @Test
    fun httpStatusCode_parses() {
        assertEquals(429, AiFailoverErrors.httpStatusCode("HTTP 429: x"))
        assertEquals(null, AiFailoverErrors.httpStatusCode("no code"))
    }
}

class AiRequestRouterTest {
    @Test
    fun failsOverToSecondSlot_on429() {
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
                if (apiKey == "bad") throw IllegalStateException("HTTP 429: quota")
                return """{"ok":true,"key":"$apiKey"}"""
            }
        }
        val slots = listOf(
            ProviderSlot("s1", AiProviderId.GEMINI, "main", true),
            ProviderSlot("s2", AiProviderId.GEMINI, "backup", true),
        )
        val keys = mapOf("s1" to "bad", "s2" to "good")
        val result = routeText(
            slots = slots,
            keys = keys,
            providers = mapOf(AiProviderId.GEMINI to failing),
            prompt = "p",
            schema = JSONObject(),
        )
        assertEquals("s2", result.slotId)
        assertTrue(result.rawResponse.contains("good"))
    }

    @Test
    fun doesNotFailover_onNonRetryableError() {
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
            ): String = throw IllegalStateException("unsupported schemaVersion=9")
        }
        try {
            routeText(
                slots = listOf(ProviderSlot("s1", AiProviderId.GEMINI, "main", true)),
                keys = mapOf("s1" to "k"),
                providers = mapOf(AiProviderId.GEMINI to failing),
                prompt = "p",
                schema = JSONObject(),
            )
            error("expected throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("schemaVersion"))
        }
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
                if (AiFailoverErrors.isFailoverable(e)) continue
                throw e
            }
        }
        throw AllAiProvidersFailedException(attempts.coerceAtLeast(1), lastError)
    }
}
