package work.temp1209.kakeibo.data.prefs

import android.content.Context
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class FileBackupPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastExportAtOrNull(): String? = prefs.getString(KEY_LAST_EXPORT_AT, null)

    fun setLastExportAt(iso: String) {
        prefs.edit().putString(KEY_LAST_EXPORT_AT, iso).apply()
    }

    fun lastImportAtOrNull(): String? = prefs.getString(KEY_LAST_IMPORT_AT, null)

    fun setLastImportAt(iso: String) {
        prefs.edit().putString(KEY_LAST_IMPORT_AT, iso).apply()
    }

    fun lastPromptShownYearMonthOrNull(): String? = prefs.getString(KEY_LAST_PROMPT_SHOWN_YM, null)

    fun setLastPromptShownYearMonth(ym: String) {
        prefs.edit().putString(KEY_LAST_PROMPT_SHOWN_YM, ym).apply()
    }

    fun hasExportedThisMonth(zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val last = lastExportAtOrNull() ?: return false
        val instant = runCatching { Instant.parse(last) }.getOrNull() ?: return false
        return YearMonth.from(instant.atZone(zone)) == YearMonth.now(zone)
    }

    companion object {
        private const val PREFS_NAME = "file_backup_prefs"
        private const val KEY_LAST_EXPORT_AT = "last_export_at"
        private const val KEY_LAST_IMPORT_AT = "last_import_at"
        private const val KEY_LAST_PROMPT_SHOWN_YM = "last_prompt_shown_ym"
    }
}
