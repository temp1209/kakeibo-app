package work.temp1209.kakeibo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisErrorSummaryTest {

    @Test
    fun rateLimit_isMadeExplicit() {
        assertEquals("利用上限に達しました", analysisErrorSummary("HTTP 429: rate limit"))
        assertEquals("利用上限に達しました", analysisErrorSummary("APIの利用上限です"))
    }

    @Test
    fun genericMessage_usesFirstSentence() {
        assertEquals(
            "ネットワーク接続を確認してください",
            analysisErrorSummary("ネットワーク接続を確認してください。再試行できます。"),
        )
    }

    @Test
    fun blankMessage_returnsNull() {
        assertNull(analysisErrorSummary("  "))
        assertNull(analysisErrorSummary(null))
    }
}
