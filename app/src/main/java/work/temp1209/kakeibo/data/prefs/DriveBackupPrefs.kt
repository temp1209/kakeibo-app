package work.temp1209.kakeibo.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.driveBackupDataStore: DataStore<Preferences> by preferencesDataStore(name = "drive_backup_prefs")

class DriveBackupPrefs(private val context: Context) {
    private val ds = context.driveBackupDataStore

    private val keyLastBackupAt = stringPreferencesKey("last_backup_at")
    private val keyLastBackupError = stringPreferencesKey("last_backup_error")
    private val keyLastBackupErrorAt = stringPreferencesKey("last_backup_error_at")
    private val keyMonthJobYm = stringPreferencesKey("month_job_year_month")
    private val keyAccountEmail = stringPreferencesKey("account_email")

    suspend fun setLastBackupAt(iso: String) {
        ds.edit { it[keyLastBackupAt] = iso }
    }

    suspend fun lastBackupAtOrNull(): String? =
        ds.data.map { it[keyLastBackupAt] }.first()

    suspend fun setLastBackupError(message: String?) {
        ds.edit {
            if (message == null) {
                it.remove(keyLastBackupError)
                it.remove(keyLastBackupErrorAt)
            } else {
                it[keyLastBackupError] = message
                it[keyLastBackupErrorAt] = java.time.Instant.now().toString()
            }
        }
    }

    suspend fun lastBackupErrorOrNull(): String? =
        ds.data.map { it[keyLastBackupError] }.first()

    suspend fun lastBackupErrorAtOrNull(): String? =
        ds.data.map { it[keyLastBackupErrorAt] }.first()

    suspend fun setLastMonthJobYearMonth(ym: String) {
        ds.edit { it[keyMonthJobYm] = ym }
    }

    suspend fun lastMonthJobYearMonthOrNull(): String? =
        ds.data.map { it[keyMonthJobYm] }.first()

    suspend fun setAccountEmail(email: String?) {
        ds.edit {
            if (email == null) it.remove(keyAccountEmail) else it[keyAccountEmail] = email
        }
    }

    suspend fun accountEmailOrNull(): String? =
        ds.data.map { it[keyAccountEmail] }.first()
}
