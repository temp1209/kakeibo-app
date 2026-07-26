package work.temp1209.kakeibo.data.prefs

import android.content.Context
import work.temp1209.kakeibo.data.notifications.NotificationHistory

data class NotificationSettings(
    val masterEnabled: Boolean = true,
    val failureEnabled: Boolean = true,
    val successEnabled: Boolean = false,
) {
    fun allowsAnalysisEvent(eventType: String): Boolean {
        if (!masterEnabled) return false
        return when (eventType) {
            NotificationHistory.TYPE_FAILED, NotificationHistory.TYPE_NEEDS_REVIEW -> failureEnabled
            NotificationHistory.TYPE_DONE -> successEnabled
            else -> false
        }
    }
}

/**
 * 通知設定は「失敗系（解析失敗・要確認）」「成功系（解析完了）」「予算系」の3項目に統合している。
 * 旧バージョン（項目別トグル）からの移行のみ、初回アクセス時に旧キーの値を引き継ぐ。
 */
class NotificationPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyIfNeeded()
    }

    fun isMasterEnabled(): Boolean = prefs.getBoolean(KEY_MASTER_ENABLED, true)

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun isFailureEnabled(): Boolean = prefs.getBoolean(KEY_FAILURE_ENABLED, true)

    fun setFailureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FAILURE_ENABLED, enabled).apply()
    }

    fun isSuccessEnabled(): Boolean = prefs.getBoolean(KEY_SUCCESS_ENABLED, false)

    fun setSuccessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUCCESS_ENABLED, enabled).apply()
    }

    fun isBudgetEnabled(): Boolean = prefs.getBoolean(KEY_BUDGET_ENABLED, false)

    fun setBudgetEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_ENABLED, enabled).apply()
    }

    fun isAnyBudgetNotificationEnabled(): Boolean = isMasterEnabled() && isBudgetEnabled()

    fun currentSettings(): NotificationSettings =
        NotificationSettings(
            masterEnabled = isMasterEnabled(),
            failureEnabled = isFailureEnabled(),
            successEnabled = isSuccessEnabled(),
        )

    fun isAnalysisNotificationEnabled(eventType: String): Boolean =
        currentSettings().allowsAnalysisEvent(eventType)

    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_FAILURE_ENABLED)) return
        if (!prefs.contains(KEY_LEGACY_ANALYSIS_FAILED_ENABLED)) return
        val failureEnabled = prefs.getBoolean(KEY_LEGACY_ANALYSIS_FAILED_ENABLED, true) ||
            prefs.getBoolean(KEY_LEGACY_NEEDS_REVIEW_ENABLED, false)
        val successEnabled = prefs.getBoolean(KEY_LEGACY_ANALYSIS_DONE_ENABLED, false)
        val budgetEnabled = prefs.getBoolean(KEY_LEGACY_BUDGET_PROGRESS_ENABLED, false) ||
            prefs.getBoolean(KEY_LEGACY_BUDGET_THRESHOLD_80_ENABLED, false) ||
            prefs.getBoolean(KEY_LEGACY_BUDGET_THRESHOLD_100_ENABLED, false)
        prefs.edit()
            .putBoolean(KEY_FAILURE_ENABLED, failureEnabled)
            .putBoolean(KEY_SUCCESS_ENABLED, successEnabled)
            .putBoolean(KEY_BUDGET_ENABLED, budgetEnabled)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_FAILURE_ENABLED = "failure_enabled"
        private const val KEY_SUCCESS_ENABLED = "success_enabled"
        private const val KEY_BUDGET_ENABLED = "budget_enabled"

        // 旧項目別トグル（移行専用。読み取りのみ）
        private const val KEY_LEGACY_ANALYSIS_FAILED_ENABLED = "analysis_failed_enabled"
        private const val KEY_LEGACY_ANALYSIS_DONE_ENABLED = "analysis_done_enabled"
        private const val KEY_LEGACY_NEEDS_REVIEW_ENABLED = "needs_review_enabled"
        private const val KEY_LEGACY_BUDGET_PROGRESS_ENABLED = "budget_progress_enabled"
        private const val KEY_LEGACY_BUDGET_THRESHOLD_80_ENABLED = "budget_threshold_80_enabled"
        private const val KEY_LEGACY_BUDGET_THRESHOLD_100_ENABLED = "budget_threshold_100_enabled"
    }
}
