package work.temp1209.kakeibo.data.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.backup.BackupExportBuilder
import work.temp1209.kakeibo.data.backup.BackupExportTypes
import work.temp1209.kakeibo.data.backup.BackupJsonCodec
import work.temp1209.kakeibo.data.backup.BackupMerge
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.db.ReceiptDao
import work.temp1209.kakeibo.data.prefs.DriveBackupPrefs
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

object DriveBackupOrchestrator {

    private const val CURRENT_MONTH_FILE = "current-month.json"

    private fun fullSnapshotFileName(ym: YearMonth): String =
        "full-${ym.year}-${ym.monthValue.toString().padStart(2, '0')}-01.json"

    suspend fun runScheduledBackup(context: Context): Result<Unit> {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return Result.success(Unit)
        return runWithAccount(context, account)
    }

    suspend fun runWithAccount(context: Context, account: GoogleSignInAccount): Result<Unit> =
        withContext(Dispatchers.IO) {
            val token = DriveBackupRepository.getAccessToken(context, account)
            val dao = AppDatabase.get(context).receiptDao()
            val prefs = DriveBackupPrefs(context)
            val zone = ZoneId.systemDefault()
            val nowYm = YearMonth.now(zone)

            val lastJobYm = prefs.lastMonthJobYearMonthOrNull()
            if (lastJobYm != nowYm.toString()) {
                runMonthBoundary(context, token, dao, nowYm, zone)
                prefs.setLastMonthJobYearMonth(nowYm.toString())
            }

            val (start, end) = BackupExportBuilder.currentMonthWindow(zone)
            val all = dao.listAllReceiptsForExport()
            val monthReceipts = all.filter { r ->
                val t = BackupExportBuilder.receiptTimestamp(r) ?: return@filter false
                !t.isBefore(start) && !t.isAfter(end)
            }
            val ids = monthReceipts.map { it.receiptId }.toSet()
            val monthItems = ids.flatMap { rid -> dao.listReceiptItems(rid) }
            val cur = BackupExportBuilder.buildFile(
                context = context,
                exportType = BackupExportTypes.CURRENT_MONTH,
                rangeStart = start,
                rangeEnd = end,
                receipts = monthReceipts,
                items = monthItems,
            )
            DriveBackupRepository.uploadOrReplaceJson(token, CURRENT_MONTH_FILE, BackupJsonCodec.toJson(cur))
            prefs.setLastBackupAt(Instant.now().toString())
            Result.success(Unit)
        }

    private suspend fun runMonthBoundary(
        context: Context,
        token: String,
        dao: ReceiptDao,
        nowYm: YearMonth,
        zone: ZoneId,
    ) {
        val archiveYm = nowYm.minusMonths(2)
        val archReceipts = dao.listAllReceiptsForExport()
            .filter { BackupExportBuilder.isReceiptInClosedMonth(it, archiveYm, zone) }
        val archItems = archReceipts.flatMap { dao.listReceiptItems(it.receiptId) }
        val archStart = archiveYm.atDay(1).atStartOfDay(zone).toInstant()
        val archEnd = archiveYm.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        val archFile = BackupExportBuilder.buildFile(
            context = context,
            exportType = BackupExportTypes.ARCHIVE_MONTH,
            rangeStart = archStart,
            rangeEnd = archEnd,
            receipts = archReceipts,
            items = archItems,
        )
        DriveBackupRepository.uploadOrReplaceJson(
            token,
            "archive-${archiveYm}.json",
            BackupJsonCodec.toJson(archFile),
        )

        val allR = dao.listAllReceiptsForExport()
        val allI = allR.flatMap { dao.listReceiptItems(it.receiptId) }
        val earliest = allR.mapNotNull { runCatching { Instant.parse(it.createdAt) }.getOrNull() }.minOrNull()
            ?: Instant.EPOCH
        val fullFile = BackupExportBuilder.buildFile(
            context = context,
            exportType = BackupExportTypes.FULL_SNAPSHOT,
            rangeStart = earliest,
            rangeEnd = Instant.now(),
            receipts = allR,
            items = allI,
        )
        DriveBackupRepository.uploadOrReplaceJson(token, fullSnapshotFileName(nowYm), BackupJsonCodec.toJson(fullFile))

        pruneFullSnapshots(token)
    }

    private suspend fun pruneFullSnapshots(token: String) {
        val files = DriveBackupRepository.listJsonFiles(token)
            .filter { it.name.startsWith("full-") && it.name.endsWith(".json") }
            .sortedByDescending { it.modifiedTimeMs }
        files.drop(2).forEach { f ->
            DriveBackupRepository.deleteById(token, f.id)
        }
    }

    suspend fun restoreMergeFromDrive(context: Context): Result<BackupMerge.MergeStats> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(IllegalStateException("Googleにログインしていません"))
        val token = DriveBackupRepository.getAccessToken(context, account)
        val dao = AppDatabase.get(context).receiptDao()
        val files = DriveBackupRepository.listJsonFiles(token)
        val bodies = mutableListOf<String>()

        files.filter { it.name.startsWith("full-") && it.name.endsWith(".json") }
            .sortedBy { it.modifiedTimeMs }
            .forEach { f ->
                DriveBackupRepository.downloadUtf8(token, f.id)?.let { bodies.add(it) }
            }

        files.filter { it.name.startsWith("archive-") && it.name.endsWith(".json") }
            .sortedBy { it.name }
            .forEach { f ->
                DriveBackupRepository.downloadUtf8(token, f.id)?.let { bodies.add(it) }
            }

        files.find { it.name == CURRENT_MONTH_FILE }?.let { f ->
            DriveBackupRepository.downloadUtf8(token, f.id)?.let { bodies.add(it) }
        }

        if (bodies.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Drive上にバックアップJSONが見つかりません"))
        }
        val stats = BackupMerge.mergeFilesIntoDb(dao, bodies)
        Result.success(stats)
    }
}
