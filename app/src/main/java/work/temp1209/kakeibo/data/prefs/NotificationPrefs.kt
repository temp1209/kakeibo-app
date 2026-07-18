package work.temp1209.kakeibo.data.prefs

import android.content.Context
import work.temp1209.kakeibo.data.notifications.NotificationHistory

data class NotificationSettings(
    val masterEnabled: Boolean = true,
    val analysisFailedEnabled: Boolean = true,
    val analysisDoneEnabled: Boolean = false,
    val needsReviewEnabled: Boolean = false,
) {
    fun allowsAnalysisEvent(eventType: String): Boolean {
        if (!masterEnabled) return false
        return when (eventType) {
            NotificationHistory.TYPE_FAILED -> analysisFailedEnabled
            NotificationHistory.TYPE_DONE -> analysisDoneEnabled
            NotificationHistory.TYPE_NEEDS_REVIEW -> needsReviewEnabled
            else -> false
        }
    }
}

class NotificationPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMasterEnabled(): Boolean = prefs.getBoolean(KEY_MASTER_ENABLED, true)

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun isAnalysisFailedEnabled(): Boolean = prefs.getBoolean(KEY_ANALYSIS_FAILED_ENABLED, true)

    fun setAnalysisFailedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYSIS_FAILED_ENABLED, enabled).apply()
    }

    fun isAnalysisDoneEnabled(): Boolean = prefs.getBoolean(KEY_ANALYSIS_DONE_ENABLED, false)

    fun setAnalysisDoneEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYSIS_DONE_ENABLED, enabled).apply()
    }

    fun isNeedsReviewEnabled(): Boolean = prefs.getBoolean(KEY_NEEDS_REVIEW_ENABLED, false)

    fun setNeedsReviewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEEDS_REVIEW_ENABLED, enabled).apply()
    }

    fun isBudgetProgressEnabled(): Boolean = prefs.getBoolean(KEY_BUDGET_PROGRESS_ENABLED, false)

    fun setBudgetProgressEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_PROGRESS_ENABLED, enabled).apply()
    }

    fun isBudgetNotificationEnabled(): Boolean =
        isMasterEnabled() && isBudgetProgressEnabled()

    fun currentSettings(): NotificationSettings =
        NotificationSettings(
            masterEnabled = isMasterEnabled(),
            analysisFailedEnabled = isAnalysisFailedEnabled(),
            analysisDoneEnabled = isAnalysisDoneEnabled(),
            needsReviewEnabled = isNeedsReviewEnabled(),
        )

    fun isAnalysisNotificationEnabled(eventType: String): Boolean =
        currentSettings().allowsAnalysisEvent(eventType)

    companion object {
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_ANALYSIS_FAILED_ENABLED = "analysis_failed_enabled"
        private const val KEY_ANALYSIS_DONE_ENABLED = "analysis_done_enabled"
        private const val KEY_NEEDS_REVIEW_ENABLED = "needs_review_enabled"
        private const val KEY_BUDGET_PROGRESS_ENABLED = "budget_progress_enabled"
    }
}
