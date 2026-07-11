package work.temp1209.kakeibo.data.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.prefs.FileBackupPrefs
import java.time.YearMonth

object MonthlyBackupPromptPolicy {

    suspend fun shouldShow(context: Context, prefs: FileBackupPrefs): Boolean = withContext(Dispatchers.IO) {
        val activeCount = AppDatabase.get(context).receiptDao().countActiveReceipts()
        if (activeCount < 1) return@withContext false
        if (prefs.hasExportedThisMonth()) return@withContext false
        val currentYm = YearMonth.now().toString()
        if (prefs.dismissedPromptYearMonthOrNull() == currentYm) return@withContext false
        if (prefs.lastPromptShownYearMonthOrNull() == currentYm) return@withContext false
        true
    }
}
