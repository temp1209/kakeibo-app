package work.temp1209.kakeibo.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.prefs.FileBackupPrefs
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.ui.backup.FileBackupUiState
import work.temp1209.kakeibo.ui.common.TabScreenTitle

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    fileBackup: FileBackupUiState,
    backupPrefs: FileBackupPrefs,
) {
    val activity = LocalContext.current as ComponentActivity
    val store = remember { GeminiApiKeyStore(activity) }
    val scope = rememberCoroutineScope()
    val gemini = remember { GeminiClient() }

    val snackbarHostState = remember { SnackbarHostState() }
    var apiKeyInput by remember { mutableStateOf("") }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

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

        Text("Gemini")
        Text(
            text = if (store.hasKey()) {
                "APIキー: 保存済み（表示はしません）"
            } else {
                "APIキー: 未設定"
            },
        )

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("Gemini APIキーを入力") },
            placeholder = { Text("AIza...") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val trimmed = apiKeyInput.trim()
                if (trimmed.isEmpty()) return@Button
                if (store.hasKey()) {
                    showOverwriteConfirm = true
                } else {
                    store.saveKey(trimmed)
                    apiKeyInput = ""
                }
            },
            enabled = apiKeyInput.isNotBlank(),
        ) {
            Text(if (store.hasKey()) "APIキーを更新" else "APIキーを保存")
        }

        Button(
            onClick = { showDeleteConfirm = true },
            enabled = store.hasKey(),
        ) {
            Text("APIキーを削除")
        }

        Button(
            onClick = {
                if (testing) return@Button
                val apiKey = store.readKeyOrNull() ?: return@Button
                testing = true
                scope.launch {
                    val msg = runCatching {
                        withContext(Dispatchers.IO) {
                            gemini.testText(apiKey)
                        }
                    }.fold(
                        onSuccess = { "疎通OK" },
                        onFailure = { "疎通NG: ${it.message ?: it.javaClass.simpleName}" },
                    )
                    snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                    testing = false
                }
            },
            enabled = store.hasKey() && !testing,
        ) {
            Text(if (testing) "疎通テスト中..." else "疎通テスト（テキスト）")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("データのバックアップ")
        Text("レシートデータを JSON ファイルに保存・復元します（画像は含みません）。")
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

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text("APIキーを更新しますか？") },
            text = { Text("既存のAPIキーを上書きします。よろしいですか？") },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = apiKeyInput.trim()
                        if (trimmed.isNotEmpty()) {
                            store.saveKey(trimmed)
                            apiKeyInput = ""
                        }
                        showOverwriteConfirm = false
                    },
                ) { Text("更新") }
            },
            dismissButton = {
                Button(onClick = { showOverwriteConfirm = false }) { Text("キャンセル") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("APIキーを削除しますか？") },
            text = { Text("削除するとGemini解析ができなくなります。") },
            confirmButton = {
                Button(
                    onClick = {
                        store.deleteKey()
                        apiKeyInput = ""
                        showDeleteConfirm = false
                    },
                ) { Text("削除") }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            },
        )
    }
}
