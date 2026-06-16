package work.temp1209.kakeibo.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.UserRecoverableAuthException
import work.temp1209.kakeibo.data.drive.DriveBackupOrchestrator
import work.temp1209.kakeibo.data.drive.DriveBackupUserMessages
import work.temp1209.kakeibo.data.drive.DriveHttpException
import work.temp1209.kakeibo.data.drive.LocalBackupEmptyException
import work.temp1209.kakeibo.data.drive.BackupRegressionException
import work.temp1209.kakeibo.data.prefs.DriveBackupPrefs
import java.io.IOException

class DriveBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val r = DriveBackupOrchestrator.runScheduledBackup(applicationContext)
            if (r.isSuccess) {
                Result.success()
            } else {
                mapFailureToWorkResult(r.exceptionOrNull())
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Drive backup threw", e)
            mapFailureToWorkResult(e)
        }
    }

    private suspend fun mapFailureToWorkResult(e: Throwable?): Result {
        val err = e ?: return Result.retry()
        logDriveError(err)
        return when (err) {
            is LocalBackupEmptyException,
            is BackupRegressionException,
            -> Result.success()
            is UserRecoverableAuthException,
            is GoogleAuthException,
            -> {
                DriveBackupPrefs(applicationContext).setLastBackupError(
                    DriveBackupUserMessages.snackbarMessage(err),
                )
                Result.failure()
            }
            is DriveHttpException -> when (err.httpCode) {
                401, 403 -> Result.failure()
                429, in 500..599 -> Result.retry()
                else -> Result.failure()
            }
            is IOException -> Result.retry()
            else -> Result.retry()
        }
    }

    private fun logDriveError(err: Throwable) {
        if (err is DriveHttpException) {
            Log.w(
                TAG,
                "Drive backup HTTP ${err.httpCode}: ${err.responseBody ?: err.message}",
                err,
            )
        } else {
            Log.w(TAG, "Drive backup: ${err.message}", err)
        }
    }

    companion object {
        private const val TAG = "DriveBackupWorker"
    }
}
