package work.temp1209.kakeibo.ui.settings

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.ai.AiProviderId
import work.temp1209.kakeibo.data.ai.AiRequestRouter
import work.temp1209.kakeibo.data.ai.ProviderSlot
import work.temp1209.kakeibo.data.gemini.GeminiUserMessages
import work.temp1209.kakeibo.data.prefs.AiProviderStore

private val SlotRowHeight = 56.dp

@Composable
fun AiProviderSlotsSection(
    store: AiProviderStore,
    onShowMessage: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val router = remember { AiRequestRouter(store) }
    var revision by remember { mutableIntStateOf(0) }
    val slots = remember { mutableStateListOf<ProviderSlot>() }

    LaunchedEffect(revision) {
        slots.clear()
        slots.addAll(store.getConfig().orderedSlots())
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var testingSlotId by remember { mutableStateOf<String?>(null) }
    var slotPendingDelete by remember { mutableStateOf<ProviderSlot?>(null) }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { SlotRowHeight.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI / API", style = MaterialTheme.typography.titleMedium)
        Text(
            "上から順に使います。ハンドルをドラッグして優先順位を変えられます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (slots.isEmpty()) {
            Text(
                "APIキー未設定",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                slots.forEachIndexed { index, slot ->
                    val isDragging = draggingId == slot.slotId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SlotRowHeight)
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) translationY = dragOffsetY
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "並び替え",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .pointerInput(slot.slotId, slots.size) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            draggingId = slot.slotId
                                            dragOffsetY = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount
                                            val from = slots.indexOfFirst { it.slotId == slot.slotId }
                                            if (from < 0) return@detectVerticalDragGestures
                                            val target = (from + (dragOffsetY / rowHeightPx).toInt())
                                                .coerceIn(0, slots.lastIndex)
                                            if (target != from) {
                                                val item = slots.removeAt(from)
                                                slots.add(target, item)
                                                dragOffsetY -= (target - from) * rowHeightPx
                                            }
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffsetY = 0f
                                            store.setOrderedSlotIds(slots.map { it.slotId })
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffsetY = 0f
                                            revision++
                                        },
                                    )
                                },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${index + 1}. ${slot.label}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = AiProviderId.displayName(slot.providerId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            Text(if (testingSlotId == slot.slotId) "…" else "疎通")
                        }
                        IconButton(onClick = { slotPendingDelete = slot }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "削除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (index < slots.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("APIキーを追加", modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showAddDialog) {
        AddApiKeyDialog(
            defaultLabel = if (slots.isEmpty()) "メイン" else "予備${slots.size}",
            onDismiss = { showAddDialog = false },
            onConfirm = { label, apiKey ->
                store.addSlot(AiProviderId.GEMINI, label, apiKey, enabled = true)
                showAddDialog = false
                revision++
                scope.launch { onShowMessage("「$label」を追加しました") }
            },
        )
    }

    slotPendingDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { slotPendingDelete = null },
            title = { Text("削除しますか？") },
            text = { Text("「${slot.label}」を削除します。この操作は取り消せません。") },
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
                TextButton(onClick = { slotPendingDelete = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun AddApiKeyDialog(
    defaultLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (label: String, apiKey: String) -> Unit,
) {
    var label by remember { mutableStateOf(defaultLabel) }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("APIキーを追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("ラベル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini APIキー") },
                    placeholder = { Text("AIza...") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val key = apiKey.trim()
                    if (key.isEmpty()) return@Button
                    onConfirm(label.trim().ifBlank { defaultLabel }, key)
                },
                enabled = apiKey.isNotBlank(),
            ) { Text("追加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
