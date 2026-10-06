package work.temp1209.kakeibo.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHistoryDisplayTest {
    private fun display(eventType: String, status: String?) =
        NotificationHistory.historyDisplayType(eventType, status)

    @Test
    fun failedEvent_whoseReceiptSucceededLater_isShownAsResolved() {
        assertEquals(NotificationHistory.DISPLAY_FAILED_THEN_DONE, display(NotificationHistory.TYPE_FAILED, "DONE"))
        assertEquals("解析失敗→完了", NotificationHistory.eventTypeLabel(NotificationHistory.DISPLAY_FAILED_THEN_DONE))
    }

    @Test
    fun failedEvent_whileResendIsPending_isShownAsRetrying() {
        assertEquals(NotificationHistory.DISPLAY_FAILED_RETRYING, display(NotificationHistory.TYPE_FAILED, "PENDING"))
        assertEquals(NotificationHistory.DISPLAY_FAILED_RETRYING, display(NotificationHistory.TYPE_FAILED, "RUNNING"))
    }

    @Test
    fun failedEvent_whoseReceiptNeedsReviewNow_isShownAsNeedsReview() {
        assertEquals(NotificationHistory.TYPE_NEEDS_REVIEW, display(NotificationHistory.TYPE_FAILED, "NEEDS_REVIEW"))
    }

    @Test
    fun failedEvent_staysFailed_whenStillFailedOrUnknown() {
        assertEquals(NotificationHistory.TYPE_FAILED, display(NotificationHistory.TYPE_FAILED, "FAILED"))
        // レシートが削除済みなど、現在の状態が分からないときは記録どおり
        assertEquals(NotificationHistory.TYPE_FAILED, display(NotificationHistory.TYPE_FAILED, null))
    }

    @Test
    fun otherEvents_areNeverChanged() {
        assertEquals(NotificationHistory.TYPE_DONE, display(NotificationHistory.TYPE_DONE, "FAILED"))
        assertEquals(NotificationHistory.TYPE_NEEDS_REVIEW, display(NotificationHistory.TYPE_NEEDS_REVIEW, "DONE"))
    }
}
