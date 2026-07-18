package work.temp1209.kakeibo.data.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import work.temp1209.kakeibo.data.notifications.NotificationHistory

class NotificationSettingsTest {

    @Test
    fun defaults_allowOnlyAnalysisFailure() {
        val settings = NotificationSettings()

        assertTrue(settings.allowsAnalysisEvent(NotificationHistory.TYPE_FAILED))
        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_DONE))
        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_NEEDS_REVIEW))
    }

    @Test
    fun masterOff_blocksAllAnalysisNotifications() {
        val settings = NotificationSettings(
            masterEnabled = false,
            analysisFailedEnabled = true,
            analysisDoneEnabled = true,
            needsReviewEnabled = true,
        )

        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_FAILED))
        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_DONE))
        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_NEEDS_REVIEW))
    }

    @Test
    fun individualSettings_areAppliedPerEventType() {
        val settings = NotificationSettings(
            analysisFailedEnabled = false,
            analysisDoneEnabled = true,
            needsReviewEnabled = true,
        )

        assertFalse(settings.allowsAnalysisEvent(NotificationHistory.TYPE_FAILED))
        assertTrue(settings.allowsAnalysisEvent(NotificationHistory.TYPE_DONE))
        assertTrue(settings.allowsAnalysisEvent(NotificationHistory.TYPE_NEEDS_REVIEW))
        assertFalse(settings.allowsAnalysisEvent("UNKNOWN"))
    }
}
