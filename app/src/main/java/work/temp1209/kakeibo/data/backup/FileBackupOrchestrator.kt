package work.temp1209.kakeibo.data.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.necessity.NecessityPurposeId
import work.temp1209.kakeibo.data.prefs.NecessityPolicyStore
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
        val policySnapshot = NecessityPolicyStore(context).exportSnapshot()
        val policyDto = NecessityPolicyBackupDto(
            purposeId = policySnapshot.purposeId.name,
            corrections = policySnapshot.corrections,
            compiledPolicy = policySnapshot.compiledPolicy,
        )
        val file = BackupExportBuilder.buildFile(
            context = context,
            exportType = BackupExportTypes.FULL_SNAPSHOT,
            rangeStart = earliest,
            rangeEnd = Instant.now(),
            receipts = receipts,
            items = items,
            necessityPolicy = policyDto,
        )
        BackupJsonCodec.toJson(file)
    }

    suspend fun mergeFromJson(context: Context, json: String): BackupMerge.MergeStats = withContext(Dispatchers.IO) {
        val file = BackupJsonCodec.fromJson(json)
        val dao = AppDatabase.get(context).receiptDao()
        val stats = BackupMerge.mergeIntoDb(dao, file.data)
        file.necessityPolicy?.let { dto ->
            importNecessityPolicyIfNewer(context, file.exportedAt, dto)
        }
        stats
    }

    private fun importNecessityPolicyIfNewer(
        context: Context,
        exportExportedAt: String,
        dto: NecessityPolicyBackupDto,
    ) {
        val store = NecessityPolicyStore(context)
        val remoteTime = runCatching { Instant.parse(exportExportedAt) }.getOrNull() ?: return
        val localCompiled = store.getCompiledPolicyOrNull()
        val localTime = localCompiled?.compiledAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (localTime != null && !remoteTime.isAfter(localTime)) return
        val purposeId = NecessityPurposeId.fromStored(dto.purposeId)
        store.importFromBackup(purposeId, dto.corrections, dto.compiledPolicy)
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
