package work.temp1209.kakeibo.data.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import work.temp1209.kakeibo.data.db.AppDatabase

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // TODO Phase2: implement dequeue -> gemini -> db update -> notify
        // For now keep worker stubbed; queue wiring will be added next commit.
        AppDatabase.get(applicationContext).receiptDao()
        return Result.success()
    }
}

