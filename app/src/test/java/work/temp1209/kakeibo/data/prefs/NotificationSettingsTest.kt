package work.temp1209.kakeibo.data.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import work.temp1209.kakeibo.data.notifications.NotificationHistory

class NotificationSettingsTest {

    @Test
    fun defaults_allowOnlyFailureGroup() {
        val settings = NotificationSettings()

        assertTrue(settings.allowsAnalysisEvent(NotificationHistory.TYPE_FAILED))
        assertTrue(settings.allowsAnalysisEvent(NotificationHistory.TYPE_NEEDS_REVIEW))
        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_DONE))
    }

    @Test
    fun masterOff_blocksAllAnalysisNotifications() {
        val settings = NotificationSettings(
            masterEnabled = false,
            failureEnabled = true,
            successEnabled = true,
        )

        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_FAILED))
        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_DONE))
        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_NEEDS_REVIEW))
    }

    @Test
    fun failureGroup_coversFailedAndNeedsReview() {
        val settings = NotificationSettings(
            failureEnabled = false,
            successEnabled = true,
        )

        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_FAILED))
        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_NEEDS_REVIEW))
        assertTrue(settings.allowsAnalysisEvent(NotificationHistory.TYPE_DONE))
        assertFalse(settings.allowsAnalysisEvent("UNKNOWN"))
    }
}
