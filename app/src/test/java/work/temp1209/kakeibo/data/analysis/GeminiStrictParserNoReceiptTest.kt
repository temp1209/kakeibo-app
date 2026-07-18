package work.temp1209.kakeibo.data.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiStrictParserNoReceiptTest {

    @Test
    fun containsNoReceipt_acceptsBracketedAndBare() {
        assertTrue(GeminiStrictParser.containsNoReceiptWarning(listOf("[NO_RECEIPT]")))
        assertTrue(GeminiStrictParser.containsNoReceiptWarning(listOf("NO_RECEIPT")))
        assertTrue(GeminiStrictParser.containsNoReceiptWarning(listOf(" no_receipt ")))
        assertTrue(GeminiStrictParser.containsNoReceiptWarning(listOf("[読取] NO_RECEIPT")))
        assertFalse(GeminiStrictParser.containsNoReceiptWarning(listOf("[読取] 文字が薄い")))
        assertFalse(GeminiStrictParser.containsNoReceiptWarning(emptyList()))
    }
}
