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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import work.temp1209.kakeibo.data.prefs.BudgetSettings
import work.temp1209.kakeibo.data.prefs.BudgetStore
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.data.prefs.NotificationPrefs
import work.temp1209.kakeibo.ui.settings.GeminiApiKeyInputSection

private enum class OnboardingStep {
    Welcome,
    ApiKey,
    Budget,
    Camera,
    Notification,
    Done,
}

private fun parseOnboardingStep(name: String): OnboardingStep =
    OnboardingStep.entries.find { it.name == name } ?: OnboardingStep.Welcome

private fun onboardingStepCount(includeNotification: Boolean): Int =
    if (includeNotification) 6 else 5

private fun OnboardingStep.displayIndex(includeNotification: Boolean): Int = when (this) {
    OnboardingStep.Welcome -> 1
    OnboardingStep.ApiKey -> 2
    OnboardingStep.Budget -> 3
    OnboardingStep.Camera -> 4
    OnboardingStep.Notification -> 5
    OnboardingStep.Done -> if (includeNotification) 6 else 5
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
    val budgetStore = remember { BudgetStore(context) }
    val notificationPrefs = remember { NotificationPrefs(context) }
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.Welcome.name) }
    val step = remember(stepName) { parseOnboardingStep(stepName) }
    var apiKeyInput by remember { mutableStateOf("") }
    var budgetInput by rememberSaveable { mutableStateOf("") }
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
            OnboardingStep.ApiKey -> OnboardingStep.Budget
            OnboardingStep.Budget -> OnboardingStep.Camera
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
            OnboardingStep.Budget -> OnboardingStep.ApiKey
            OnboardingStep.Camera -> OnboardingStep.Budget
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

                OnboardingStep.Budget -> {
                    val budgetAmount = budgetInput.toLongOrNull()
                    val validOrEmpty = budgetInput.isEmpty() || (budgetAmount != null && budgetAmount > 0)
                    OnboardingStepTitle("月次予算")
                    OnboardingStepBody(
                        "毎月の予算を設定すると、分析画面で残りの目安を確認できます。",
                    )
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { value ->
                            if (value.length <= 12 && value.all(Char::isDigit)) {
                                budgetInput = value
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("月額予算（円）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = budgetInput.isNotEmpty() && !validOrEmpty,
                        supportingText = {
                            if (budgetInput.isEmpty()) {
                                Text("空のままスキップできます。設定から後で変更できます。")
                            } else if (!validOrEmpty) {
                                Text("1円以上の予算を入力してください")
                            }
                        },
                    )
                    OnboardingNavRow(
                        showBack = true,
                        onBack = { previousStep(OnboardingStep.Budget) },
                        primaryLabel = if (budgetInput.isEmpty()) "スキップ" else "保存して次へ",
                        primaryEnabled = validOrEmpty,
                        onPrimary = {
                            budgetStore.save(
                                BudgetSettings(
                                    enabled = budgetAmount != null && budgetAmount > 0,
                                    monthlyBudgetYen = budgetAmount ?: 0,
                                ),
                            )
                            nextStep(OnboardingStep.Budget)
                        },
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
                        description = "解析に失敗したときや、予算のペースを確認したいときにお知らせできます。",
                        permissionNote = "初期設定では解析失敗のお知らせだけが有効です。設定から変更できます。",
                        permission = Manifest.permission.POST_NOTIFICATIONS,
                        deniedHint = "拒否してもアプリは使えます。通知は端末の設定から後で許可できます。",
                        onBack = { previousStep(OnboardingStep.Notification) },
                        onNext = {
                            autoSkippedNotification = true
                            nextStep(OnboardingStep.Notification)
                        },
                        shouldAutoAdvanceIfGranted = !autoSkippedNotification,
                        onPermissionDecision = { granted ->
                            notificationPrefs.setMasterEnabled(granted)
                            notificationPrefs.setAnalysisFailedEnabled(granted)
                            notificationPrefs.setAnalysisDoneEnabled(false)
                            notificationPrefs.setNeedsReviewEnabled(false)
                            notificationPrefs.setBudgetProgressEnabled(false)
                        },
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
    onPermissionDecision: (Boolean) -> Unit = {},
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
            onPermissionDecision(true)
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
        onPrimary = {
            onPermissionDecision(granted)
            onNext()
        },
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
    primaryEnabled: Boolean = true,
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
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
            ) { Text(primaryLabel) }
        }
    }
}
