package work.temp1209.kakeibo.ui.backup

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.backup.BackupUserMessages
import work.temp1209.kakeibo.data.backup.FileBackupOrchestrator
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.prefs.FileBackupPrefs
import java.time.Instant

data class FileBackupUiState(
    val exporting: Boolean,
    val importing: Boolean,
    val launchExport: () -> Unit,
    val launchImport: () -> Unit,
)

private data class ImportConfirmState(
    val json: String,
    val localActive: Int,
    val backupActive: Int,
    val exportedAt: String?,
)

@Composable
fun rememberFileBackupUi(
    onMessage: (String) -> Unit,
): FileBackupUiState {
    val activity = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val backupPrefs = remember { FileBackupPrefs(activity) }

    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ImportConfirmState?>(null) }

    suspend fun performImport(json: String) {
        importing = true
        try {
            val stats = withContext(Dispatchers.IO) {
                FileBackupOrchestrator.mergeFromJson(activity, json)
            }
            backupPrefs.setLastImportAt(Instant.now().toString())
            onMessage(BackupUserMessages.mergeResult(stats))
        } catch (e: Exception) {
            onMessage(BackupUserMessages.snackbarMessage(e))
        } finally {
            importing = false
            pendingImport = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) {
            exporting = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    FileBackupOrchestrator.buildFullSnapshotJson(activity)
                }
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("ファイルを開けませんでした")
                }
                backupPrefs.setLastExportAt(Instant.now().toString())
                onMessage(BackupUserMessages.exportSuccess())
            } catch (e: Exception) {
                onMessage(BackupUserMessages.snackbarMessage(e))
            } finally {
                exporting = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            importing = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("ファイルを読み込めませんでした")
                }
                val file = FileBackupOrchestrator.parseFile(json)
                val backupActive = FileBackupOrchestrator.countActiveInFile(file)
                val localActive = withContext(Dispatchers.IO) {
                    AppDatabase.get(activity).receiptDao().countActiveReceipts()
                }
                pendingImport = ImportConfirmState(
                    json = json,
                    localActive = localActive,
                    backupActive = backupActive,
                    exportedAt = file.exportedAt,
                )
            } catch (e: Exception) {
                onMessage(BackupUserMessages.snackbarMessage(e))
            } finally {
                importing = false
            }
        }
    }

    pendingImport?.let { confirm ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("バックアップを復元しますか？") },
            text = {
                Text(
                    BackupUserMessages.importConfirmMessage(
                        localActive = confirm.localActive,
                        backupActive = confirm.backupActive,
                        exportedAt = confirm.exportedAt,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = confirm.json
                        pendingImport = null
                        scope.launch { performImport(json) }
                    },
                ) { Text("復元する") }
            },
            dismissButton = {
                Button(onClick = { pendingImport = null }) { Text("キャンセル") }
            },
        )
    }

    return FileBackupUiState(
        exporting = exporting,
        importing = importing,
        launchExport = {
            if (exporting) return@FileBackupUiState
            exporting = true
            exportLauncher.launch(FileBackupOrchestrator.defaultExportFileName())
        },
        launchImport = {
            if (importing) return@FileBackupUiState
            importing = true
            importLauncher.launch(arrayOf("application/json"))
        },
    )
}
