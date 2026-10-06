package work.temp1209.kakeibo.data.gemini

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import work.temp1209.kakeibo.data.ai.AiProvider
import work.temp1209.kakeibo.data.ai.AiProviderId
import work.temp1209.kakeibo.data.ai.AiRequestRouter
import work.temp1209.kakeibo.data.ai.AllAiProvidersFailedException
import work.temp1209.kakeibo.data.ai.ProviderSlot
import java.io.IOException
import java.net.SocketTimeoutException

class GeminiTransientFailureTest {
    private fun allFailed(cause: Throwable?) = AllAiProvidersFailedException(attempts = 2, cause = cause)

    @Test
    fun http503_isTransient() {
        assertTrue(GeminiUserMessages.isTransientFailure(allFailed(IllegalStateException("HTTP 503: UNAVAILABLE"))))
    }

    @Test
    fun http429_isTransient() {
        assertTrue(GeminiUserMessages.isTransientFailure(allFailed(IllegalStateException("HTTP 429: RESOURCE_EXHAUSTED"))))
    }

    @Test
    fun networkErrors_areTransient() {
        assertTrue(GeminiUserMessages.isTransientFailure(allFailed(SocketTimeoutException("timeout"))))
        assertTrue(GeminiUserMessages.isTransientFailure(allFailed(IOException("Unable to resolve host"))))
    }

    @Test
    fun clientErrors_areNotTransient() {
        assertFalse(GeminiUserMessages.isTransientFailure(allFailed(IllegalStateException("HTTP 400: API_KEY_INVALID"))))
        assertFalse(GeminiUserMessages.isTransientFailure(allFailed(IllegalStateException("HTTP 404: model not found"))))
    }

    @Test
    fun clientError_whoseBodyMentions429_isNotTransient() {
        // 本文に偶然「429」やクォータの語が入っていても、HTTPコードが400なら再試行しない
        val body = """HTTP 400: {"error":{"message":"Request contains an invalid argument. (token count 429)"}}"""
        assertFalse(GeminiUserMessages.isTransientFailure(allFailed(IllegalStateException(body))))
    }

    @Test
    fun failuresOutsideTheRouter_areNotTransient() {
        // 画像読込失敗・パース失敗などはルータの外で起きる。再試行しても直らない
        assertFalse(GeminiUserMessages.isTransientFailure(IllegalStateException("receipt image missing")))
        assertFalse(GeminiUserMessages.isTransientFailure(IOException("HTTP 503")))
        assertFalse(GeminiUserMessages.isTransientFailure(allFailed(null)))
    }

    @Test
    fun anyTransientSlotFailure_makesItTransient() {
        val e = AllAiProvidersFailedException(
            attempts = 2,
            cause = IllegalStateException("HTTP 400: API_KEY_INVALID"),
            allCauses = listOf(
                IllegalStateException("HTTP 503: UNAVAILABLE"),
                IllegalStateException("HTTP 400: API_KEY_INVALID"),
            ),
        )
        assertTrue(GeminiUserMessages.isTransientFailure(e))
    }

    @Test
    fun router_collectsEverySlotFailure() {
        val provider = object : AiProvider {
            override val providerId = AiProviderId.GEMINI
            override fun testConnectivity(apiKey: String) = error("unused")
            override fun generateStrictJsonFromImage(
                apiKey: String,
                jpegBytes: ByteArray,
                prompt: String,
                responseJsonSchema: JSONObject,
            ): String = throw IllegalStateException(
                if (apiKey == "sub") "HTTP 503: UNAVAILABLE" else "HTTP 400: API_KEY_INVALID",
            )
            override fun generateStrictJsonFromText(
                apiKey: String,
                prompt: String,
                responseJsonSchema: JSONObject,
            ) = error("unused")
        }
        val router = AiRequestRouter(
            resolveSlots = {
                listOf(
                    ProviderSlot("s1", AiProviderId.GEMINI, "sub", true) to "sub",
                    ProviderSlot("s2", AiProviderId.GEMINI, "main", true) to "main",
                )
            },
            providers = mapOf(AiProviderId.GEMINI to provider),
        )
        try {
            router.generateStrictJsonFromImage(ByteArray(0), "p", JSONObject())
            fail("expected AllAiProvidersFailedException")
        } catch (e: AllAiProvidersFailedException) {
            assertEquals(2, e.allCauses.size)
            // 最後に失敗したスロットは400だが、先に503があったので一時的な失敗として扱う
            assertTrue(GeminiUserMessages.isTransientFailure(e))
        }
    }
}
