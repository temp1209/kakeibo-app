package work.temp1209.kakeibo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.ai.AiProviderId
import work.temp1209.kakeibo.data.ai.AiRequestRouter
import work.temp1209.kakeibo.data.ai.ProviderSlot
import work.temp1209.kakeibo.data.gemini.GeminiUserMessages
import work.temp1209.kakeibo.data.prefs.AiProviderStore

@Composable
fun AiProviderSlotsSection(
    store: AiProviderStore,
    onShowMessage: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val router = remember { AiRequestRouter(store) }
    var revision by remember { mutableIntStateOf(0) }
    val config = remember(revision) { store.getConfig() }
    val slots = remember(revision) { config.orderedSlots() }

    var newLabel by remember { mutableStateOf("") }
    var newApiKey by remember { mutableStateOf("") }
    var testingSlotId by remember { mutableStateOf<String?>(null) }
    var slotPendingDelete by remember { mutableStateOf<ProviderSlot?>(null) }
    var slotPendingKeyUpdate by remember { mutableStateOf<ProviderSlot?>(null) }
    var keyUpdateInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI / API", style = MaterialTheme.typography.titleMedium)
        Text(
            "上から順に試します。利用上限（429）などで失敗したとき、次のキーへ自動切替します。",
            style = MaterialTheme.typography.bodySmall,
        )

        if (slots.isEmpty()) {
            Text("APIキー未設定", style = MaterialTheme.typography.bodyMedium)
        }

        slots.forEachIndexed { index, slot ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${index + 1}. ${slot.label}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${AiProviderId.displayName(slot.providerId)} · " +
                                if (store.readApiKey(slot.slotId) != null) "キー保存済み" else "キーなし",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = slot.enabled,
                        onCheckedChange = {
                            store.updateSlotMeta(slot.slotId, enabled = it)
                            revision++
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            store.moveSlot(slot.slotId, -1)
                            revision++
                        },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上へ")
                    }
                    IconButton(
                        onClick = {
                            store.moveSlot(slot.slotId, 1)
                            revision++
                        },
                        enabled = index < slots.lastIndex,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下へ")
                    }
                    TextButton(
                        onClick = {
                            if (testingSlotId != null) return@TextButton
                            testingSlotId = slot.slotId
                            scope.launch {
                                val msg = runCatching {
                                    withContext(Dispatchers.IO) { router.testSlot(slot.slotId) }
                                }.fold(
                                    onSuccess = { "疎通OK（${slot.label}）" },
                                    onFailure = {
                                        GeminiUserMessages.userFacingError(
                                            it,
                                            GeminiUserMessages.Operation.CONNECTIVITY_TEST,
                                        )
                                    },
                                )
                                onShowMessage(msg)
                                testingSlotId = null
                            }
                        },
                        enabled = testingSlotId == null && store.readApiKey(slot.slotId) != null,
                    ) {
                        Text(if (testingSlotId == slot.slotId) "テスト中…" else "疎通")
                    }
                    TextButton(
                        onClick = {
                            keyUpdateInput = ""
                            slotPendingKeyUpdate = slot
                        },
                    ) {
                        Text("キー更新")
                    }
                    TextButton(onClick = { slotPendingDelete = slot }) {
                        Text("削除")
                    }
                }
            }
        }

        Text("スロットを追加", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = newLabel,
            onValueChange = { newLabel = it },
            label = { Text("ラベル（任意）") },
            placeholder = { Text("予備") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = newApiKey,
            onValueChange = { newApiKey = it },
            label = { Text("Gemini APIキー") },
            placeholder = { Text("AIza...") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val key = newApiKey.trim()
                if (key.isEmpty()) return@Button
                val label = newLabel.trim().ifBlank {
                    if (slots.isEmpty()) "メイン" else "予備${slots.size}"
                }
                store.addSlot(AiProviderId.GEMINI, label, key, enabled = true)
                newLabel = ""
                newApiKey = ""
                revision++
                scope.launch { onShowMessage("スロット「$label」を追加しました") }
            },
            enabled = newApiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("スロットを追加")
        }
    }

    slotPendingDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { slotPendingDelete = null },
            title = { Text("スロットを削除しますか？") },
            text = { Text("「${slot.label}」のAPIキーも削除されます。") },
            confirmButton = {
                Button(
                    onClick = {
                        store.removeSlot(slot.slotId)
                        slotPendingDelete = null
                        revision++
                    },
                ) { Text("削除") }
            },
            dismissButton = {
                Button(onClick = { slotPendingDelete = null }) { Text("キャンセル") }
            },
        )
    }

    slotPendingKeyUpdate?.let { slot ->
        AlertDialog(
            onDismissRequest = { slotPendingKeyUpdate = null },
            title = { Text("APIキーを更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("「${slot.label}」のキーを上書きします。")
                    OutlinedTextField(
                        value = keyUpdateInput,
                        onValueChange = { keyUpdateInput = it },
                        label = { Text("新しい APIキー") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val key = keyUpdateInput.trim()
                        if (key.isEmpty()) return@Button
                        store.updateSlotApiKey(slot.slotId, key)
                        slotPendingKeyUpdate = null
                        keyUpdateInput = ""
                        revision++
                        scope.launch { onShowMessage("「${slot.label}」のキーを更新しました") }
                    },
                    enabled = keyUpdateInput.isNotBlank(),
                ) { Text("更新") }
            },
            dismissButton = {
                Button(onClick = { slotPendingKeyUpdate = null }) { Text("キャンセル") }
            },
        )
    }
}
