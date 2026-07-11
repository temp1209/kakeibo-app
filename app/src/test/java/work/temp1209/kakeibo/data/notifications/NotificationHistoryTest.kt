package work.temp1209.kakeibo.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHistoryTest {

    @Test
    fun eventTypeLabel_maps_known_types() {
        assertEquals("解析完了", NotificationHistory.eventTypeLabel(NotificationHistory.TYPE_DONE))
        assertEquals("要確認", NotificationHistory.eventTypeLabel(NotificationHistory.TYPE_NEEDS_REVIEW))
        assertEquals("解析失敗", NotificationHistory.eventTypeLabel(NotificationHistory.TYPE_FAILED))
    }
}
