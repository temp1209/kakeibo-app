package work.temp1209.kakeibo.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.domain.CategoryCatalog
import work.temp1209.kakeibo.data.domain.PaymentMethodCatalog
import work.temp1209.kakeibo.data.domain.ReceiptRequiredFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewScreen(
    contentPadding: PaddingValues,
    receiptId: String,
    repo: ReceiptRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var loading by remember(receiptId) { mutableStateOf(true) }
    var baseReceipt by remember(receiptId) { mutableStateOf<ReceiptEntity?>(null) }
    var items by remember(receiptId) { mutableStateOf<List<ReceiptItemEntity>>(emptyList()) }

    var merchant by remember(receiptId) { mutableStateOf("") }
    var receiptDatetime by remember(receiptId) { mutableStateOf("") }
    var totalText by remember(receiptId) { mutableStateOf("") }
    var paymentCode by remember(receiptId) { mutableStateOf("UNKNOWN") }
    var paymentMenu by remember { mutableStateOf(false) }

    var errorText by remember(receiptId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    LaunchedEffect(receiptId) {
        loading = true
        try {
            val r = repo.getReceiptOrNull(receiptId)
            baseReceipt = r
            if (r != null) {
                items = repo.listReceiptItems(receiptId)
                merchant = r.merchantName.orEmpty()
                receiptDatetime = r.receiptDatetime.orEmpty()
                totalText = r.totalAmountYen?.toString().orEmpty()
                paymentCode = r.paymentMethod?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
            } else {
                items = emptyList()
            }
        } finally {
            loading = false
        }
    }

    if (loading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text("読み込み中…", modifier = Modifier.padding(top = 12.dp))
        }
        return
    }

    val br = baseReceipt
    if (br == null) {
        Column(Modifier.padding(contentPadding).padding(16.dp)) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("レシートが見つかりません。")
        }
        return
    }

    if (br.deletedAt != null) {
        Column(Modifier.padding(contentPadding).padding(16.dp)) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("このレシートは削除済みのため修正できません。")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
            Text("要確認の修正", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("必須項目・支払手段・明細の必須度を入力し、「保存して確定」を押してください。", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("店名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = receiptDatetime,
                onValueChange = { receiptDatetime = it },
                label = { Text("取引日時（ISO8601 例: 2026-05-04T12:30:00Z）") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it.filter { ch -> ch.isDigit() } },
                label = { Text("合計金額（円・整数）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ExposedDropdownMenuBox(
                expanded = paymentMenu,
                onExpandedChange = { paymentMenu = !paymentMenu },
            ) {
                OutlinedTextField(
                    value = PaymentMethodCatalog.label(paymentCode),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("支払手段") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                DropdownMenu(expanded = paymentMenu, onDismissRequest = { paymentMenu = false }) {
                    PaymentMethodCatalog.codesOrdered.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(PaymentMethodCatalog.label(code)) },
                            onClick = {
                                paymentCode = code
                                paymentMenu = false
                            },
                        )
                    }
                }
            }

            Text("明細（調整行は集計用）", fontWeight = FontWeight.SemiBold)

            for (line in items.sortedBy { it.lineIndex }) {
                if (line.isAdjustment == 1) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            "調整行: ${line.lineTotalYen}円（表示のみ）",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else {
                    val latest = items.first { it.itemId == line.itemId }
                    key(latest.itemId) {
                        ItemReviewEditor(
                            line = latest,
                            onUpdate = { newItem ->
                                items = items.map { if (it.itemId == newItem.itemId) newItem else it }
                            },
                        )
                    }
                }
            }

            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    errorText = null
                    val total = totalText.toLongOrNull()
                    val updated = br.copy(
                        merchantName = merchant.trim().ifBlank { null },
                        receiptDatetime = receiptDatetime.trim().ifBlank { null },
                        totalAmountYen = total,
                        paymentMethod = paymentCode,
                    )
                    scope.launch {
                        val result = repo.applyReceiptReview(updated, items)
                        if (result.isSuccess) {
                            onSaved()
                        } else {
                            errorText = result.exceptionOrNull()?.message ?: "保存に失敗しました"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存して確定（要確認解除）")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemReviewEditor(
    line: ReceiptItemEntity,
    onUpdate: (ReceiptItemEntity) -> Unit,
) {
    val needsCategoryFix = ReceiptRequiredFields.itemsWithInvalidCategory(listOf(line)).isNotEmpty()
    var majorMenu by remember { mutableStateOf(false) }
    var minorMenu by remember { mutableStateOf(false) }
    var major by remember(line.itemId) { mutableStateOf(line.categoryMajor) }
    var minor by remember(line.itemId) { mutableStateOf(line.categoryMinor) }
    LaunchedEffect(line.itemId, line.categoryMajor, line.categoryMinor) {
        major = line.categoryMajor
        minor = line.categoryMinor
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(line.itemName, fontWeight = FontWeight.Medium)
            Text("${line.lineTotalYen}円 ×${line.quantity}", style = MaterialTheme.typography.bodySmall)

            if (needsCategoryFix) {
                ExposedDropdownMenuBox(
                    expanded = majorMenu,
                    onExpandedChange = { majorMenu = !majorMenu },
                ) {
                    OutlinedTextField(
                        value = CategoryCatalog.majorLabel(major),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("大カテゴリ（要修正）") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = majorMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    DropdownMenu(expanded = majorMenu, onDismissRequest = { majorMenu = false }) {
                        CategoryCatalog.majorsOrdered.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    major = code
                                    val minors = CategoryCatalog.minorsFor(code)
                                    minor = minors.firstOrNull() ?: "その他"
                                    majorMenu = false
                                    onUpdate(line.copy(categoryMajor = major, categoryMinor = minor))
                                },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = minorMenu,
                    onExpandedChange = { minorMenu = !minorMenu },
                ) {
                    OutlinedTextField(
                        value = minor,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("小カテゴリ") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minorMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    DropdownMenu(expanded = minorMenu, onDismissRequest = { minorMenu = false }) {
                        CategoryCatalog.minorsFor(major).forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    minor = m
                                    minorMenu = false
                                    onUpdate(line.copy(categoryMajor = major, categoryMinor = minor))
                                },
                            )
                        }
                    }
                }
            }

            Text("必須度スコア: ${line.necessityScore}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = line.necessityScore.toFloat(),
                onValueChange = { v ->
                    onUpdate(line.copy(necessityScore = v.toInt().coerceIn(0, 100)))
                },
                valueRange = 0f..100f,
                steps = 99,
            )
        }
    }
}
