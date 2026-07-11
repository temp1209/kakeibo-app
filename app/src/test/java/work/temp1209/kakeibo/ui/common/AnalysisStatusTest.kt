package work.temp1209.kakeibo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisStatusTest {

    @Test
    fun failed_takes_priority_over_needsReview() {
        val display = resolveAnalysisStatusDisplay("FAILED", needsReview = 1)
        assertEquals(AnalysisStatusKind.Failed, display.kind)
        assertEquals("解析失敗", display.label)
        assertTrue(display.showBadge)
    }

    @Test
    fun needsReview_from_flag_or_status() {
        assertEquals(
            "要確認",
            resolveAnalysisStatusDisplay("DONE", needsReview = 1).label,
        )
        assertEquals(
            "要確認",
            resolveAnalysisStatusDisplay("NEEDS_REVIEW", needsReview = 0).label,
        )
    }

    @Test
    fun pending_and_running_labels() {
        assertEquals("解析待ち", resolveAnalysisStatusDisplay("PENDING", 0).label)
        assertEquals("解析中", resolveAnalysisStatusDisplay("RUNNING", 0).label)
    }

    @Test
    fun done_without_needsReview_has_no_badge() {
        val display = resolveAnalysisStatusDisplay("DONE", needsReview = 0)
        assertEquals(AnalysisStatusKind.None, display.kind)
        assertFalse(display.showBadge)
    }
}
