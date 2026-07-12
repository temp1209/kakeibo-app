package work.temp1209.kakeibo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.analysis.NecessityRescoreWorker
import work.temp1209.kakeibo.data.necessity.CompiledNecessityPolicy
import work.temp1209.kakeibo.data.necessity.NecessityCorrection
import work.temp1209.kakeibo.data.necessity.NecessityPurposeId
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NecessityPolicySection(
    repo: ReceiptRepository,
    onShowMessage: suspend (String) -> Unit,
    onOpenApiKeySection: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { repo.necessityPolicyStore() }
    val scope = rememberCoroutineScope()

    var purposeId by remember { mutableStateOf(store.getPurposeId()) }
    var purposeMenuExpanded by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf(store.getEffectiveSummary()) }
    var corrections by remember { mutableStateOf(store.listCorrections()) }
    var correctionGroups by remember {
        mutableStateOf<List<ReceiptRepository.CorrectionReceiptGroup>>(emptyList())
    }
    var pending by remember { mutableStateOf(store.hasPendingCorrections()) }
    var promptBlock by remember { mutableStateOf(store.getEffectivePromptBlock()) }
    var compiling by remember { mutableStateOf(false) }
    var rescoreRunning by remember { mutableStateOf(false) }
    var showFullPolicy by remember { mutableStateOf(false) }
    var showRescoreOffer by remember { mutableStateOf(false) }
    var pendingPolicyConfirm by remember { mutableStateOf<CompiledNecessityPolicy?>(null) }
    var lastRescoreWorkState by remember { mutableStateOf<WorkInfo.State?>(null) }

    fun refresh() {
        purposeId = store.getPurposeId()
        summary = store.getEffectiveSummary()
        corrections = store.listCorrections()
        pending = store.hasPendingCorrections()
        promptBlock = store.getEffectivePromptBlock()
    }

    LaunchedEffect(corrections) {
        correctionGroups = withContext(Dispatchers.IO) {
            repo.groupCorrectionsByReceipt(corrections)
        }
    }

    LaunchedEffect(Unit) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(NecessityRescoreWorker.UNIQUE_WORK_NAME)
            .collect { infos ->
                val info = infos.firstOrNull()
                val state = info?.state
                rescoreRunning = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
                if (lastRescoreWorkState != WorkInfo.State.FAILED && state == WorkInfo.State.FAILED) {
                    val err = info?.outputData?.getString(NecessityRescoreWorker.KEY_ERROR_MESSAGE)
                    if (!err.isNullOrBlank()) {
                        onShowMessage(err)
                    }
                }
                lastRescoreWorkState = state
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("必須度の方針", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "レシート解析時の必須度スコアに反映する方針を設定します。",
            style = MaterialTheme.typography.bodySmall,
        )

        if (pending) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "訂正例が未反映です。「方針を保存してコンパイル」で反映してください。",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        ExposedDropdownMenuBox(
            expanded = purposeMenuExpanded,
            onExpandedChange = { purposeMenuExpanded = !purposeMenuExpanded },
        ) {
            OutlinedTextField(
                value = purposeId.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("目的プリセット") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = purposeMenuExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            DropdownMenu(
                expanded = purposeMenuExpanded,
                onDismissRequest = { purposeMenuExpanded = false },
            ) {
                NecessityPurposeId.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            purposeId = option
                            purposeMenuExpanded = false
                        },
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("現在の方針", style = MaterialTheme.typography.labelMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showFullPolicy = true }) {
                    Text("判定基準の全文を見る")
                }
            }
        }

        if (correctionGroups.isNotEmpty()) {
            Text("訂正例", style = MaterialTheme.typography.labelLarge)
            correctionGroups.forEach { group ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            group.merchantLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        group.corrections.forEach { correction ->
                            CorrectionRow(
                                correction = correction,
                                onRemove = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            repo.removeNecessityCorrection(correction.correctionId)
                                        }
                                        refresh()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        } else {
            Text("訂正例はまだありません。レシート修正画面で必須度を変更すると自動で追加されます。", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                if (compiling) return@Button
                if (!GeminiApiKeyStore(context).hasKey()) {
                    scope.launch {
                        onShowMessage(GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE)
                        onOpenApiKeySection()
                    }
                    return@Button
                }
                compiling = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repo.compileNecessityPolicy(purposeId)
                    }
                    compiling = false
                    when (result) {
                        ReceiptRepository.CompileNecessityPolicyResult.ApiKeyMissing -> {
                            onShowMessage(GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE)
                            onOpenApiKeySection()
                        }
                        is ReceiptRepository.CompileNecessityPolicyResult.Failure -> {
                            onShowMessage(result.message)
                        }
                        is ReceiptRepository.CompileNecessityPolicyResult.Success -> {
                            pendingPolicyConfirm = result.policy
                        }
                    }
                }
            },
            enabled = !compiling,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (compiling) "コンパイル中…" else "方針を保存してコンパイル")
        }

        Button(
            onClick = {
                if (rescoreRunning) return@Button
                if (!GeminiApiKeyStore(context).hasKey()) {
                    scope.launch {
                        onShowMessage(GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE)
                        onOpenApiKeySection()
                    }
                    return@Button
                }
                repo.scheduleNecessityRescore()
                scope.launch { onShowMessage("今月の明細の再計算を開始しました") }
            },
            enabled = !rescoreRunning && store.getCompiledPolicyOrNull() != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (rescoreRunning) "再計算中…" else "今月の明細を新しい方針で再計算する")
        }
    }

    if (showFullPolicy) {
        AlertDialog(
            onDismissRequest = { showFullPolicy = false },
            title = { Text("判定基準の全文") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(promptBlock, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullPolicy = false }) { Text("閉じる") }
            },
        )
    }

    pendingPolicyConfirm?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingPolicyConfirm = null },
            title = { Text("新しい必須度の方針") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(preview.userSummary, style = MaterialTheme.typography.bodyMedium)
                    if (preview.userRulesBlock.isNotBlank()) {
                        Text("判定ルール", style = MaterialTheme.typography.labelMedium)
                        Text(preview.userRulesBlock, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "この方針でよろしいですか？ OK を押すと訂正例はクリアされます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val policy = preview
                        pendingPolicyConfirm = null
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.commitNecessityPolicy(policy) }
                            refresh()
                            onShowMessage("方針を保存しました")
                            showRescoreOffer = true
                        }
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPolicyConfirm = null }) { Text("キャンセル") }
            },
        )
    }

    if (showRescoreOffer) {
        AlertDialog(
            onDismissRequest = { showRescoreOffer = false },
            title = { Text("方針を反映しました") },
            text = { Text("今月の明細を新しい方針で再計算しますか？（任意）") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRescoreOffer = false
                        repo.scheduleNecessityRescore()
                        scope.launch { onShowMessage("今月の明細の再計算を開始しました") }
                    },
                ) { Text("再計算する") }
            },
            dismissButton = {
                TextButton(onClick = { showRescoreOffer = false }) { Text("あとで") }
            },
        )
    }
}

@Composable
private fun CorrectionRow(
    correction: NecessityCorrection,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("「${correction.phrase}」: ${correction.scoreBefore} → ${correction.scoreAfter}")
            correction.sourceItemName?.let {
                Text("商品: $it", style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = onRemove) { Text("削除") }
    }
}
