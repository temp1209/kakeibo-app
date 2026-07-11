package work.temp1209.kakeibo.data.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.db.AppDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object FileBackupOrchestrator {

    private const val REGRESSION_MARGIN = 2

    suspend fun buildFullSnapshotJson(context: Context): String = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).receiptDao()
        if (dao.countActiveReceipts() == 0) {
            throw LocalBackupEmptyException()
        }
        val receipts = dao.listAllReceiptsForExport()
        val items = receipts.flatMap { dao.listReceiptItems(it.receiptId) }
        val earliest = receipts
            .mapNotNull { runCatching { Instant.parse(it.createdAt) }.getOrNull() }
            .minOrNull() ?: Instant.EPOCH
        val file = BackupExportBuilder.buildFile(
            context = context,
            exportType = BackupExportTypes.FULL_SNAPSHOT,
            rangeStart = earliest,
            rangeEnd = Instant.now(),
            receipts = receipts,
            items = items,
        )
        BackupJsonCodec.toJson(file)
    }

    suspend fun mergeFromJson(context: Context, json: String): BackupMerge.MergeStats = withContext(Dispatchers.IO) {
        val file = BackupJsonCodec.fromJson(json)
        val dao = AppDatabase.get(context).receiptDao()
        BackupMerge.mergeIntoDb(dao, file.data)
    }

    fun countActiveInFile(file: KakeiboBackupFile): Int =
        file.data.receipts.count { it.deletedAt == null }

    fun parseFile(json: String): KakeiboBackupFile = BackupJsonCodec.fromJson(json)

    /**
     * ローカルにデータがある状態で、バックアップ件数が大幅に多い場合は確認用例外を投げる。
     * ローカルが空のときは復元意図とみなしチェックしない。
     */
    fun assertImportLooksIntentional(localActiveCount: Int, backupActiveCount: Int) {
        if (localActiveCount == 0) return
        if (backupActiveCount > localActiveCount + REGRESSION_MARGIN) {
            throw BackupRegressionException(
                localActiveCount = localActiveCount,
                backupActiveCount = backupActiveCount,
            )
        }
    }

    fun defaultExportFileName(): String {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return "kakeibo-backup-$date.json"
    }
}
