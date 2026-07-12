package work.temp1209.kakeibo.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.ui.settings.GeminiApiKeyInputSection

private enum class OnboardingStep {
    Welcome,
    ApiKey,
    Camera,
    Notification,
    Done,
}

private fun parseOnboardingStep(name: String): OnboardingStep =
    OnboardingStep.entries.find { it.name == name } ?: OnboardingStep.Welcome

private fun onboardingStepCount(includeNotification: Boolean): Int =
    if (includeNotification) 5 else 4

private fun OnboardingStep.displayIndex(includeNotification: Boolean): Int = when (this) {
    OnboardingStep.Welcome -> 1
    OnboardingStep.ApiKey -> 2
    OnboardingStep.Camera -> 3
    OnboardingStep.Notification -> 4
    OnboardingStep.Done -> if (includeNotification) 5 else 4
}

@Composable
private fun OnboardingStepIndicator(current: OnboardingStep) {
    val includeNotification = Build.VERSION.SDK_INT >= 33
    Text(
        text = "${current.displayIndex(includeNotification)} / ${onboardingStepCount(includeNotification)}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OnboardingStepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun OnboardingStepBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun OnboardingStepHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun OnboardingWizard(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val apiKeyStore = remember { GeminiApiKeyStore(context) }
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.Welcome.name) }
    val step = remember(stepName) { parseOnboardingStep(stepName) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKeyOverwriteConfirm by remember { mutableStateOf(false) }
    var autoSkippedCamera by rememberSaveable { mutableStateOf(false) }
    var autoSkippedNotification by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(stepName, step) {
        if (step.name != stepName) {
            stepName = step.name
        }
    }

    fun nextStep(from: OnboardingStep) {
        stepName = when (from) {
            OnboardingStep.Welcome -> OnboardingStep.ApiKey
            OnboardingStep.ApiKey -> OnboardingStep.Camera
            OnboardingStep.Camera -> {
                if (Build.VERSION.SDK_INT >= 33) OnboardingStep.Notification else OnboardingStep.Done
            }
            OnboardingStep.Notification -> OnboardingStep.Done
            OnboardingStep.Done -> OnboardingStep.Done
        }.name
    }

    fun advanceFromApiKey() {
        nextStep(OnboardingStep.ApiKey)
    }

    fun onApiKeyNext() {
        val trimmed = apiKeyInput.trim()
        if (trimmed.isEmpty()) {
            advanceFromApiKey()
            return
        }
        if (apiKeyStore.hasKey()) {
            showApiKeyOverwriteConfirm = true
        } else {
            apiKeyStore.saveKey(trimmed)
            apiKeyInput = ""
            advanceFromApiKey()
        }
    }

    fun previousStep(from: OnboardingStep) {
        stepName = when (from) {
            OnboardingStep.ApiKey -> OnboardingStep.Welcome
            OnboardingStep.Camera -> OnboardingStep.ApiKey
            OnboardingStep.Notification -> OnboardingStep.Camera
            OnboardingStep.Done -> {
                if (Build.VERSION.SDK_INT >= 33) OnboardingStep.Notification else OnboardingStep.Camera
            }
            OnboardingStep.Welcome -> OnboardingStep.Welcome
        }.name
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OnboardingStepIndicator(step)
            when (step) {
                OnboardingStep.Welcome -> {
                    OnboardingStepTitle("ようこそ")
                    OnboardingStepBody(
                        "レシートを撮るだけで家計簿がつきます。\n" +
                            "最初に、解析と撮影に必要な設定をします。",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = { nextStep(OnboardingStep.Welcome) }) {
                            Text("次へ")
                        }
                    }
                }

                OnboardingStep.ApiKey -> {
                    OnboardingStepTitle("Gemini APIキー")
                    OnboardingStepBody(
                        "レシートの AI 解析に必要です。Google AI Studio 等で取得したキーを入力してください。",
                    )
                    GeminiApiKeyInputSection(
                        store = apiKeyStore,
                        apiKeyInput = apiKeyInput,
                        onApiKeyInputChange = { apiKeyInput = it },
                        showStatusLine = false,
                        showSaveButton = false,
                    )
                    Text(
                        text = GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OnboardingStepHint(
                        "空のまま「次へ」でスキップできます（設定タブからいつでも入力可能）。",
                    )
                    OnboardingNavRow(
                        showBack = true,
                        onBack = { previousStep(OnboardingStep.ApiKey) },
                        primaryLabel = "次へ",
                        onPrimary = { onApiKeyNext() },
                    )
                }

                OnboardingStep.Camera -> {
                    OnboardingPermissionStep(
                        title = "カメラの許可",
                        description = "レシートを撮影するためにカメラの使用を許可してください。",
                        permissionNote = "カメラはレシートの撮影に使います。",
                        permission = Manifest.permission.CAMERA,
                        deniedHint = "拒否した場合は、端末の設定から後から許可できます。",
                        onBack = { previousStep(OnboardingStep.Camera) },
                        onNext = {
                            autoSkippedCamera = true
                            nextStep(OnboardingStep.Camera)
                        },
                        shouldAutoAdvanceIfGranted = !autoSkippedCamera,
                    )
                }

                OnboardingStep.Notification -> {
                    OnboardingPermissionStep(
                        title = "通知の許可",
                        description = "解析が終わったらお知らせします。通知を許可してください。",
                        permissionNote = "通知は解析完了のお知らせに使います。",
                        permission = Manifest.permission.POST_NOTIFICATIONS,
                        deniedHint = "拒否してもアプリは使えますが、解析完了のお知らせは表示されません。",
                        onBack = { previousStep(OnboardingStep.Notification) },
                        onNext = {
                            autoSkippedNotification = true
                            nextStep(OnboardingStep.Notification)
                        },
                        shouldAutoAdvanceIfGranted = !autoSkippedNotification,
                    )
                }

                OnboardingStep.Done -> {
                    OnboardingStepTitle("準備完了")
                    OnboardingStepBody(
                        "レシートを撮影して送信してください。\n" +
                            "データのバックアップは、設定から JSON ファイルで保存できます。",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = onFinished) { Text("はじめる") }
                    }
                }
            }
        }
    }

    if (showApiKeyOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showApiKeyOverwriteConfirm = false },
            title = { Text("APIキーを更新しますか？") },
            text = { Text("既存のAPIキーを上書きします。よろしいですか？") },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = apiKeyInput.trim()
                        if (trimmed.isNotEmpty()) {
                            apiKeyStore.saveKey(trimmed)
                            apiKeyInput = ""
                            advanceFromApiKey()
                        }
                        showApiKeyOverwriteConfirm = false
                    },
                ) { Text("更新") }
            },
            dismissButton = {
                Button(onClick = { showApiKeyOverwriteConfirm = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun OnboardingPermissionStep(
    title: String,
    description: String,
    permissionNote: String,
    permission: String,
    deniedHint: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    shouldAutoAdvanceIfGranted: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember(permission) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    fun refreshGranted() {
        granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            granted = isGranted
        },
    )

    LaunchedEffect(permission) {
        refreshGranted()
    }

    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(granted, shouldAutoAdvanceIfGranted) {
        if (granted && shouldAutoAdvanceIfGranted) {
            onNext()
        }
    }

    OnboardingStepTitle(title)
    OnboardingStepBody(description)
    OnboardingStepHint(permissionNote)
    if (granted) {
        Text("許可済みです。", color = MaterialTheme.colorScheme.primary)
    } else {
        OnboardingStepHint(deniedHint)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!granted) {
            Button(onClick = { launcher.launch(permission) }) {
                Text("許可する")
            }
        }
    }

    OnboardingNavRow(
        showBack = true,
        onBack = onBack,
        primaryLabel = "次へ",
        onPrimary = onNext,
        showPrimary = !granted || !shouldAutoAdvanceIfGranted,
    )
}

@Composable
private fun OnboardingNavRow(
    showBack: Boolean,
    onBack: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    showPrimary: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (showBack) {
            TextButton(onClick = onBack) { Text("戻る") }
        } else {
            Text("")
        }
        if (showPrimary) {
            Button(onClick = onPrimary) { Text(primaryLabel) }
        }
    }
}
