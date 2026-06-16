package work.temp1209.kakeibo.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.drive.DriveBackupOrchestrator
import work.temp1209.kakeibo.data.drive.DriveBackupUserMessages
import work.temp1209.kakeibo.data.drive.DriveBackupRepository
import work.temp1209.kakeibo.data.drive.GoogleSignInHelper
import work.temp1209.kakeibo.data.drive.LocalBackupEmptyException
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.prefs.DriveBackupPrefs
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.ui.common.TabScreenTitle

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
) {
    val activity = LocalContext.current as ComponentActivity
    val store = remember { GeminiApiKeyStore(activity) }
    val drivePrefs = remember { DriveBackupPrefs(activity) }
    val scope = rememberCoroutineScope()
    val gemini = remember { GeminiClient() }

    val snackbarHostState = remember { SnackbarHostState() }
    var apiKeyInput by remember { mutableStateOf("") }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    var driveEmail by remember { mutableStateOf<String?>(null) }
    var lastDriveBackup by remember { mutableStateOf<String?>(null) }
    var lastDriveBackupError by remember { mutableStateOf<String?>(null) }
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var showDriveReLoginDialog by remember { mutableStateOf(false) }
    var driveReLoginHint by remember { mutableStateOf<String?>(null) }
    var showRestorePromptDialog by remember { mutableStateOf(false) }

    val signInClient = remember(activity) {
        GoogleSignIn.getClient(activity, GoogleSignInHelper.signInOptions())
    }

    suspend fun refreshDriveStatus() {
        driveEmail = GoogleSignIn.getLastSignedInAccount(activity)?.email ?: drivePrefs.accountEmailOrNull()
        lastDriveBackup = drivePrefs.lastBackupAtOrNull()
        lastDriveBackupError = drivePrefs.lastBackupErrorOrNull()
    }

    suspend fun handleDriveBackupFailure(error: Throwable) {
        val message = DriveBackupUserMessages.snackbarMessage(error)
        snackbarHostState.showSnackbar(message = message, withDismissAction = true)
        lastDriveBackupError = message
        if (DriveBackupUserMessages.suggestsRestore(error)) {
            showRestorePromptDialog = true
            return
        }
        if (DriveBackupUserMessages.suggestsReLogin(error)) {
            driveReLoginHint = DriveBackupUserMessages.recoveryHint(error)
            showDriveReLoginDialog = true
        }
    }

    suspend fun signOutDrive() {
        signInClient.signOut()
        drivePrefs.setAccountEmail(null)
        driveEmail = null
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        task.addOnSuccessListener { acc ->
            scope.launch {
                drivePrefs.setAccountEmail(acc.email)
                driveEmail = acc.email
                if (!GoogleSignInHelper.hasDriveAppDataScope(acc)) {
                    handleDriveBackupFailure(
                        IllegalStateException("drive.appdata スコープが付与されていません"),
                    )
                    return@launch
                }
                val dao = AppDatabase.get(activity).receiptDao()
                val localCount = withContext(Dispatchers.IO) { dao.countActiveReceipts() }
                val hasRemote = withContext(Dispatchers.IO) {
                    val token = DriveBackupRepository.getAccessToken(activity, acc)
                    DriveBackupRepository.hasBackupFiles(token)
                }
                snackbarHostState.showSnackbar(
                    message = if (hasRemote && localCount == 0) {
                        "Googleドライブに接続しました。データを戻すには復元を実行してください。"
                    } else {
                        "Googleドライブに接続しました"
                    },
                    withDismissAction = true,
                )
                if (hasRemote && localCount == 0) {
                    showRestorePromptDialog = true
                }
                refreshDriveStatus()
            }
        }
        task.addOnFailureListener { e ->
            val code = (e as? ApiException)?.statusCode
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Googleログイン失敗: ${e.message} ($code)",
                    withDismissAction = true,
                )
            }
        }
    }

    fun launchDriveSignIn() {
        signInLauncher.launch(signInClient.signInIntent)
    }

    LaunchedEffect(Unit) {
        refreshDriveStatus()
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

        Text("Googleドライブ（appDataFolder）")
        Text(
            "接続アカウント: ${driveEmail ?: "未接続"}",
        )
        Text("最終バックアップ: ${lastDriveBackup ?: "—"}")
        if (!lastDriveBackupError.isNullOrBlank()) {
            Text(
                text = "直近のバックアップエラー: $lastDriveBackupError",
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { launchDriveSignIn() },
        ) {
            Text("Googleでログインしてバックアップ")
        }

        Button(
            onClick = {
                scope.launch {
                    signOutDrive()
                    snackbarHostState.showSnackbar("ログアウトしました", withDismissAction = true)
                }
            },
            enabled = driveEmail != null || GoogleSignIn.getLastSignedInAccount(activity) != null,
        ) {
            Text("ログアウト")
        }

        Button(
            onClick = {
                if (backingUp) return@Button
                val account = GoogleSignIn.getLastSignedInAccount(activity)
                if (account == null) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "先にGoogleでログインしてください",
                            withDismissAction = true,
                        )
                    }
                    return@Button
                }
                if (!GoogleSignInHelper.hasDriveAppDataScope(account)) {
                    scope.launch {
                        driveReLoginHint = DriveBackupUserMessages.RELOGIN_HINT
                        showDriveReLoginDialog = true
                    }
                    return@Button
                }
                backingUp = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        DriveBackupOrchestrator.runScheduledBackup(activity)
                    }
                    if (r.isSuccess) {
                        snackbarHostState.showSnackbar("バックアップ完了", withDismissAction = true)
                    } else {
                        handleDriveBackupFailure(r.exceptionOrNull() ?: IllegalStateException("不明なエラー"))
                    }
                    refreshDriveStatus()
                    backingUp = false
                }
            },
            enabled = !backingUp && GoogleSignIn.getLastSignedInAccount(activity) != null,
        ) {
            Text(if (backingUp) "バックアップ中…" else "今すぐバックアップ")
        }

        Button(
            onClick = {
                if (restoring) return@Button
                restoring = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        DriveBackupOrchestrator.restoreMergeFromDrive(activity)
                    }
                    snackbarHostState.showSnackbar(
                        if (r.isSuccess) {
                            val s = r.getOrNull()
                            buildString {
                                append("復元マージ完了（反映 ${s?.receiptsApplied}件、スキップ ${s?.receiptsSkipped}件）")
                                if ((s?.jsonParseFailures ?: 0) > 0) {
                                    append("、JSON無効 ${s?.jsonParseFailures}件")
                                }
                            }
                        } else {
                            val err = r.exceptionOrNull() ?: IllegalStateException("不明なエラー")
                            "復元失敗: ${DriveBackupUserMessages.snackbarMessage(err)}"
                        },
                        withDismissAction = true,
                    )
                    if (r.isFailure) {
                        val err = r.exceptionOrNull()
                        if (err != null && DriveBackupUserMessages.suggestsReLogin(err)) {
                            driveReLoginHint = DriveBackupUserMessages.recoveryHint(err)
                            showDriveReLoginDialog = true
                        }
                    }
                    restoring = false
                }
            },
            enabled = !restoring && GoogleSignIn.getLastSignedInAccount(activity) != null,
        ) {
            Text(if (restoring) "復元中…" else "Driveから復元（マージ）")
        }
    }

    if (showRestorePromptDialog) {
        AlertDialog(
            onDismissRequest = { showRestorePromptDialog = false },
            title = { Text("Driveから復元しますか？") },
            text = {
                Text(
                    "ローカルにレシートがありません。ログイン時に自動バックアップは行いません。" +
                        "Driveに保存済みのデータがあれば、復元で戻せます。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestorePromptDialog = false
                        if (restoring) return@Button
                        restoring = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                DriveBackupOrchestrator.restoreMergeFromDrive(activity)
                            }
                            snackbarHostState.showSnackbar(
                                if (r.isSuccess) {
                                    val s = r.getOrNull()
                                    buildString {
                                        append("復元マージ完了（反映 ${s?.receiptsApplied}件、スキップ ${s?.receiptsSkipped}件）")
                                        if ((s?.jsonParseFailures ?: 0) > 0) {
                                            append("、JSON無効 ${s?.jsonParseFailures}件")
                                        }
                                    }
                                } else {
                                    val err = r.exceptionOrNull() ?: IllegalStateException("不明なエラー")
                                    "復元失敗: ${DriveBackupUserMessages.snackbarMessage(err)}"
                                },
                                withDismissAction = true,
                            )
                            restoring = false
                        }
                    },
                ) { Text("復元する") }
            },
            dismissButton = {
                Button(onClick = { showRestorePromptDialog = false }) { Text("あとで") }
            },
        )
    }

    if (showDriveReLoginDialog) {
        AlertDialog(
            onDismissRequest = { showDriveReLoginDialog = false },
            title = { Text("Drive権限エラー") },
            text = {
                Text(
                    buildString {
                        append(driveReLoginHint ?: DriveBackupUserMessages.RELOGIN_HINT)
                        append("\n\n一度ログアウトしてから再ログインすると、drive.appdata の許可を再度求められます。")
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDriveReLoginDialog = false
                        scope.launch {
                            signOutDrive()
                            launchDriveSignIn()
                        }
                    },
                ) { Text("再ログイン") }
            },
            dismissButton = {
                Button(onClick = { showDriveReLoginDialog = false }) { Text("閉じる") }
            },
        )
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
