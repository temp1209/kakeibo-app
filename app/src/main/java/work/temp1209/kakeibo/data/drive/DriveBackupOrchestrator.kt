package work.temp1209.kakeibo.data.drive

import android.content.Context
import androidx.room.withTransaction
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

    suspend fun runScheduledBackup(context: Context): Result<Unit> {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return Result.success(Unit)
        return runWithAccount(context, account)
    }

    suspend fun runWithAccount(context: Context, account: GoogleSignInAccount): Result<Unit> =
        withContext(Dispatchers.IO) {
            val prefs = DriveBackupPrefs(context)
            runCatching {
                val token = DriveBackupRepository.getAccessToken(context, account)
                val dao = AppDatabase.get(context).receiptDao()
                val localCount = dao.countActiveReceipts()
                val remoteExists = DriveBackupRepository.hasBackupFiles(token)

                if (localCount == 0) {
                    if (remoteExists) {
                        throw LocalBackupEmptyException(remoteBackupExists = true)
                    }
                    return@runCatching
                }

                val zone = ZoneId.systemDefault()
                val nowYm = YearMonth.now(zone)
                val all = dao.listAllReceiptsForExport()
                var manifest = DriveBackupRepository.readManifest(token)
                    ?: BackupManifest(updatedAt = Instant.now().toString())

                val lastJobYm = prefs.lastMonthJobYearMonthOrNull()
                if (lastJobYm != nowYm.toString() && all.isNotEmpty()) {
                    manifest = runMonthBoundary(context, token, dao, nowYm, zone, localCount, manifest)
                    DriveBackupRepository.writeManifest(token, manifest)
                    prefs.setLastMonthJobYearMonth(nowYm.toString())
                } else if (lastJobYm != nowYm.toString()) {
                    prefs.setLastMonthJobYearMonth(nowYm.toString())
                }

                val (start, end) = BackupExportBuilder.currentMonthWindow(zone)
                val monthReceipts = all.filter { r ->
                    BackupExportBuilder.isInCurrentMonthExport(r, start, end)
                }
                val monthActiveCount = monthReceipts.count { it.deletedAt == null }
                BackupSafetyGuard.assertSafeToUpload(
                    token = token,
                    localActiveCount = localCount,
                    exportActiveCount = monthActiveCount,
                )

                val ids = monthReceipts.map { it.receiptId }.toSet()
                val monthItems = ids.flatMap { rid -> dao.listReceiptItems(rid) }
                val curFile = BackupExportBuilder.buildFile(
                    context = context,
                    exportType = BackupExportTypes.CURRENT_MONTH,
                    rangeStart = start,
                    rangeEnd = end,
                    receipts = monthReceipts,
                    items = monthItems,
                )
                val curName = SnapshotFileNames.forExport(BackupExportTypes.CURRENT_MONTH)
                val curJson = BackupJsonCodec.toJson(curFile)
                val curId = DriveBackupRepository.uploadImmutableSnapshot(token, curName, curJson)

                manifest = manifest.copy(
                    updatedAt = Instant.now().toString(),
                    currentMonth = curFile.toManifestRef(curId, curName),
                )

                DriveBackupRepository.writeManifest(token, manifest)
                DriveBackupRepository.pruneUnreferenced(token, manifest.allReferencedFileIds())

                prefs.setLastBackupAt(Instant.now().toString())
                prefs.setLastBackupError(null)
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { e ->
                    prefs.setLastBackupError(DriveBackupUserMessages.snackbarMessage(e))
                    Result.failure(e)
                },
            )
        }

    private suspend fun runMonthBoundary(
        context: Context,
        token: String,
        dao: ReceiptDao,
        nowYm: YearMonth,
        zone: ZoneId,
        localActiveCount: Int,
        manifest: BackupManifest,
    ): BackupManifest {
        val allR = dao.listAllReceiptsForExport()
        BackupSafetyGuard.assertSafeToUpload(
            token = token,
            localActiveCount = localActiveCount,
            exportActiveCount = allR.count { it.deletedAt == null },
        )

        var updated = manifest

        val archiveYm = nowYm.minusMonths(2)
        val archReceipts = allR.filter { BackupExportBuilder.isReceiptInClosedMonth(it, archiveYm, zone) }
        if (archReceipts.isNotEmpty()) {
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
            val ymKey = archiveYm.toString()
            val archName = SnapshotFileNames.forExport(BackupExportTypes.ARCHIVE_MONTH, "-$ymKey")
            val archId = DriveBackupRepository.uploadImmutableSnapshot(
                token,
                archName,
                BackupJsonCodec.toJson(archFile),
            )
            val archRef = ManifestArchiveRef(
                yearMonth = ymKey,
                fileId = archId,
                fileName = archName,
                activeReceiptCount = archFile.data.receipts.count { it.deletedAt == null },
            )
            updated = updated.copy(
                archives = updated.archives.filter { it.yearMonth != ymKey } + archRef,
            )
        }

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
        val fullName = SnapshotFileNames.forExport(BackupExportTypes.FULL_SNAPSHOT)
        val fullId = DriveBackupRepository.uploadImmutableSnapshot(
            token,
            fullName,
            BackupJsonCodec.toJson(fullFile),
        )
        val fullRef = fullFile.toManifestRef(fullId, fullName)

        return updated.copy(
            updatedAt = Instant.now().toString(),
            previousFullSnapshot = updated.fullSnapshot,
            fullSnapshot = fullRef,
        )
    }

    suspend fun restoreMergeFromDrive(context: Context): Result<BackupMerge.MergeStats> = withContext(Dispatchers.IO) {
        runCatching {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: throw IllegalStateException("Googleにログインしていません")
            val token = DriveBackupRepository.getAccessToken(context, account)
            val manifest = DriveBackupRepository.readManifest(token)
                ?: throw IllegalStateException("Drive上に manifest.json が見つかりません。バックアップが未作成か、旧形式のみの可能性があります。")

            val bodies = mutableListOf<String>()

            manifest.previousFullSnapshot?.let { ref ->
                bodies += DriveBackupRepository.downloadRequired(token, ref.fileId, "前回フルスナップショット")
            }
            manifest.fullSnapshot?.let { ref ->
                bodies += DriveBackupRepository.downloadRequired(token, ref.fileId, "フルスナップショット")
            }
            manifest.archives.sortedBy { it.yearMonth }.forEach { ref ->
                bodies += DriveBackupRepository.downloadRequired(
                    token,
                    ref.fileId,
                    "アーカイブ ${ref.yearMonth}",
                )
            }
            manifest.currentMonth?.let { ref ->
                bodies += DriveBackupRepository.downloadRequired(token, ref.fileId, "当月スナップショット")
            }

            if (bodies.isEmpty()) {
                throw IllegalStateException("manifest に有効なスナップショット参照がありません")
            }

            val db = AppDatabase.get(context)
            val dao = db.receiptDao()
            db.withTransaction {
                BackupMerge.mergeFilesIntoDb(dao, bodies)
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) },
        )
    }
}
