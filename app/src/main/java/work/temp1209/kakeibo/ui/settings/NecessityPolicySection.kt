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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import work.temp1209.kakeibo.data.necessity.NecessityCorrection
import work.temp1209.kakeibo.data.necessity.NecessityPurposeId
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore

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
    var summary by remember { mutableStateOf(store.getEffectiveSummary()) }
    var corrections by remember { mutableStateOf(store.listCorrections()) }
    var pending by remember { mutableStateOf(store.hasPendingCorrections()) }
    var promptBlock by remember { mutableStateOf(store.getEffectivePromptBlock()) }
    var compiling by remember { mutableStateOf(false) }
    var rescoreRunning by remember { mutableStateOf(false) }
    var showFullPolicy by remember { mutableStateOf(false) }
    var showRescoreOffer by remember { mutableStateOf(false) }

    fun refresh() {
        purposeId = store.getPurposeId()
        summary = store.getEffectiveSummary()
        corrections = store.listCorrections()
        pending = store.hasPendingCorrections()
        promptBlock = store.getEffectivePromptBlock()
    }

    LaunchedEffect(Unit) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(NecessityRescoreWorker.UNIQUE_WORK_NAME)
            .collect { infos ->
                rescoreRunning = infos.any {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
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

        Text("目的プリセット", style = MaterialTheme.typography.labelLarge)
        NecessityPurposeId.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = purposeId == option,
                    onClick = { purposeId = option },
                )
                Text(option.label, modifier = Modifier.padding(start = 4.dp))
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

        if (corrections.isNotEmpty()) {
            Text("訂正例", style = MaterialTheme.typography.labelLarge)
            corrections.forEach { correction ->
                CorrectionRow(
                    correction = correction,
                    onRemove = {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.removeNecessityCorrection(correction.correctionId) }
                            refresh()
                        }
                    },
                )
            }
        } else {
            Text("訂正例はまだありません。明細画面から追加できます。", style = MaterialTheme.typography.bodySmall)
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
                            onShowMessage("コンパイル失敗: ${result.message}")
                        }
                        is ReceiptRepository.CompileNecessityPolicyResult.Success -> {
                            refresh()
                            onShowMessage("方針を保存しました")
                            showRescoreOffer = true
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("「${correction.phrase}」→ ${correction.direction.toPromptLabel()}")
                correction.sourceItemName?.let {
                    Text("元: $it", style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = onRemove) { Text("削除") }
        }
    }
}
