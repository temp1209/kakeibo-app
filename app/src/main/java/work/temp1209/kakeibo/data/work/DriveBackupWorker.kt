package work.temp1209.kakeibo.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import work.temp1209.kakeibo.data.drive.DriveBackupOrchestrator

class DriveBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val r = DriveBackupOrchestrator.runScheduledBackup(applicationContext)
        return if (r.isSuccess) {
            Result.success()
        } else {
            Log.w(TAG, "Drive backup: ${r.exceptionOrNull()?.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DriveBackupWorker"
    }
}
