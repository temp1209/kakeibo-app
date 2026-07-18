package work.temp1209.kakeibo.data.prefs

import android.content.Context
import java.time.YearMonth

class BudgetNotificationStateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sentReasons(yearMonth: YearMonth): Set<String> =
        prefs.getStringSet(key(yearMonth), emptySet()).orEmpty().toSet()

    fun markSent(yearMonth: YearMonth, reasons: Set<String>) {
        if (reasons.isEmpty()) return
        val merged = sentReasons(yearMonth) + reasons
        prefs.edit().putStringSet(key(yearMonth), merged).apply()
        removeOldEntries(yearMonth)
    }

    private fun removeOldEntries(current: YearMonth) {
        val oldestToKeep = current.minusMonths(2)
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .forEach { storedKey ->
                val month = runCatching {
                    YearMonth.parse(storedKey.removePrefix(KEY_PREFIX))
                }.getOrNull()
                if (month != null && month.isBefore(oldestToKeep)) {
                    editor.remove(storedKey)
                }
            }
        editor.apply()
    }

    private fun key(yearMonth: YearMonth): String = "$KEY_PREFIX$yearMonth"

    companion object {
        private const val PREFS_NAME = "budget_notification_state"
        private const val KEY_PREFIX = "sent_reasons_"
    }
}
