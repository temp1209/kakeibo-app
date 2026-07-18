package work.temp1209.kakeibo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.prefs.AiProviderStore
import work.temp1209.kakeibo.data.prefs.FileBackupPrefs
import work.temp1209.kakeibo.data.prefs.NotificationPrefs
import work.temp1209.kakeibo.ui.backup.FileBackupUiState
import work.temp1209.kakeibo.ui.common.TabScreenTitle

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    repo: ReceiptRepository,
    fileBackup: FileBackupUiState,
    backupPrefs: FileBackupPrefs,
) {
    val context = LocalContext.current
    val providerStore = remember { AiProviderStore(context) }
    val notificationPrefs = remember { NotificationPrefs(context) }

    val snackbarHostState = remember { SnackbarHostState() }

    var lastExportAt by remember { mutableStateOf<String?>(null) }
    var lastImportAt by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        lastExportAt = backupPrefs.lastExportAtOrNull()
        lastImportAt = backupPrefs.lastImportAtOrNull()
    }

    LaunchedEffect(fileBackup.exporting, fileBackup.importing) {
        if (!fileBackup.exporting && !fileBackup.importing) {
            lastExportAt = backupPrefs.lastExportAtOrNull()
            lastImportAt = backupPrefs.lastImportAtOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabScreenTitle("設定")
        SnackbarHost(hostState = snackbarHostState)

        AiProviderSlotsSection(
            store = providerStore,
            onShowMessage = { msg ->
                snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        NotificationSettingsSection(prefs = notificationPrefs)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        NecessityPolicySection(
            repo = repo,
            onShowMessage = { msg -> snackbarHostState.showSnackbar(message = msg, withDismissAction = true) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("データのバックアップ")
        Text("レシートデータを JSON ファイルに保存・復元します（画像は含みません）。")
        Text("APIキーはバックアップに含まれません。")
        Text("最終エクスポート: ${lastExportAt ?: "—"}")
        Text("最終インポート: ${lastImportAt ?: "—"}")

        Button(
            onClick = fileBackup.launchExport,
            enabled = !fileBackup.exporting,
        ) {
            Text(if (fileBackup.exporting) "エクスポート中…" else "JSON をエクスポート")
        }

        Button(
            onClick = fileBackup.launchImport,
            enabled = !fileBackup.importing,
        ) {
            Text(if (fileBackup.importing) "インポート中…" else "JSON から復元（マージ）")
        }
    }
}
