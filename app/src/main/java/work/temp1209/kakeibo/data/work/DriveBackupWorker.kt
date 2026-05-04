package work.temp1209.kakeibo.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.UserRecoverableAuthException
import work.temp1209.kakeibo.data.drive.DriveBackupOrchestrator
import work.temp1209.kakeibo.data.drive.DriveHttpException
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

    private fun mapFailureToWorkResult(e: Throwable?): Result {
        val err = e ?: return Result.retry()
        Log.w(TAG, "Drive backup: ${err.message}", err)
        return when (err) {
            is UserRecoverableAuthException,
            is GoogleAuthException,
            -> Result.success()
            is DriveHttpException -> when (err.httpCode) {
                401, 403 -> Result.success()
                429, in 500..599 -> Result.retry()
                else -> Result.success()
            }
            is IOException -> Result.retry()
            else -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "DriveBackupWorker"
    }
}
