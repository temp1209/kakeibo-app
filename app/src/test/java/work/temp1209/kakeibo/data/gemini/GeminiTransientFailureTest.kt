package work.temp1209.kakeibo.data.gemini

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import work.temp1209.kakeibo.data.ai.AllAiProvidersFailedException
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
    fun failuresOutsideTheRouter_areNotTransient() {
        // 画像読込失敗・パース失敗などはルータの外で起きる。再試行しても直らない
        assertFalse(GeminiUserMessages.isTransientFailure(IllegalStateException("receipt image missing")))
        assertFalse(GeminiUserMessages.isTransientFailure(IOException("HTTP 503")))
        assertFalse(GeminiUserMessages.isTransientFailure(allFailed(null)))
    }
}
