package work.temp1209.kakeibo.data.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import work.temp1209.kakeibo.data.analysis.model.GeminiReceiptResponse
import work.temp1209.kakeibo.data.analysis.model.ReceiptHeader

class GeminiStrictParserReviewFlagsTest {

    private fun response(receiptDatetime: String) = GeminiReceiptResponse(
        schemaVersion = "1.0",
        receipt = ReceiptHeader(
            receiptDatetime = receiptDatetime,
            merchantName = "テスト店",
            totalAmountYen = 450,
        ),
        items = emptyList(),
    )

    @Test
    fun sameDayAsSaved_noAnomaly() {
        val flags = GeminiStrictParser.reviewFlags(
            response("2026-08-04T17:21:00+09:00"),
            savedAt = "2026-08-04T08:30:00Z", // 2026-08-04 17:30 JST
        )
        assertFalse(flags.needsReview)
    }

    @Test
    fun withinTwoMonthsPast_noAnomaly() {
        val flags = GeminiStrictParser.reviewFlags(
            response("2026-06-10T12:00:00+09:00"),
            savedAt = "2026-08-04T08:30:00Z",
        )
        assertFalse(flags.needsReview)
    }

    @Test
    fun receiptDateAfterSavedTime_flaggedAsFuture() {
        val flags = GeminiStrictParser.reviewFlags(
            response("2026-08-10T12:00:00+09:00"),
            savedAt = "2026-08-04T08:30:00Z",
        )
        assertTrue(flags.needsReview)
        assertTrue(flags.reasons.any { it.contains("future", ignoreCase = true) })
    }

    @Test
    fun misreadYearTwoYearsAgo_flaggedAsStale() {
        // Regression: なか卯レシート「260804(=2026-08-04)」が2024年と誤読されたケースを模す。
        val flags = GeminiStrictParser.reviewFlags(
            response("2024-08-04T17:21:00+09:00"),
            savedAt = "2026-08-04T08:30:00Z",
        )
        assertTrue(flags.needsReview)
        assertTrue(flags.reasons.any { it.contains("misread", ignoreCase = true) })
    }

    @Test
    fun noSavedAt_skipsCheck() {
        val flags = GeminiStrictParser.reviewFlags(
            response("2024-08-04T17:21:00+09:00"),
            savedAt = null,
        )
        assertFalse(flags.needsReview)
    }
}
