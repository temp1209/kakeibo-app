package work.temp1209.kakeibo.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.temp1209.kakeibo.data.isQueueEntryStale
import work.temp1209.kakeibo.data.isRetryDue
import java.time.Duration
import java.time.Instant

class RetryScheduleTest {
    @Test
    fun retryDelays_are5_10_20_40_60_minutes_thenGiveUp() {
        assertEquals(listOf(5L, 10L, 20L, 40L, 60L), (1..5).map { AnalysisWorker.retryDelayMinutes(it) })
        assertNull(AnalysisWorker.retryDelayMinutes(6))
    }

    @Test
    fun retryDue_comparesAgainstNow() {
        val now = Instant.parse("2026-10-07T12:00:00Z")
        assertFalse(isRetryDue("2026-10-07T12:00:01Z", now))
        assertTrue(isRetryDue("2026-10-07T12:00:00Z", now))
        assertTrue(isRetryDue("2026-10-07T11:00:00.5Z", now))
    }

    @Test
    fun retryDue_missingOrBrokenDeadline_isTreatedAsDue() {
        val now = Instant.now()
        assertTrue(isRetryDue(null, now))
        assertTrue(isRetryDue("not-a-date", now))
    }

    @Test
    fun retryWaitEntries_canGoStale() {
        val now = Instant.parse("2026-10-14T12:00:00Z")
        val queuedAt = Instant.parse("2026-10-01T12:00:00Z")
        assertTrue(isQueueEntryStale(AnalysisWorker.STATUS_RETRY_WAIT, queuedAt, now, Duration.ofDays(7)))
    }
}
