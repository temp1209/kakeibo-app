package work.temp1209.kakeibo.ui.detail

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptImageEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.data.domain.CategoryCatalog
import work.temp1209.kakeibo.data.domain.PaymentMethodCatalog
import work.temp1209.kakeibo.data.domain.ReceiptRequiredFields
import work.temp1209.kakeibo.ui.format.formatIsoInstant
import work.temp1209.kakeibo.ui.format.formatYen
import work.temp1209.kakeibo.ui.common.AnalysisStatusBadge
import work.temp1209.kakeibo.ui.common.AnalysisStatusKind
import work.temp1209.kakeibo.ui.common.GeminiJsonViewerSheet
import work.temp1209.kakeibo.ui.common.analysisStatusDisplay

@Composable
fun ReceiptDetailScreen(
    contentPadding: PaddingValues,
    receiptId: String,
    loadReceipt: suspend (String) -> ReceiptEntity?,
    loadVisibleItems: suspend (String) -> List<ReceiptItemEntity>,
    loadImage: suspend (String) -> ReceiptImageEntity?,
    loadGeminiJson: suspend (String) -> String?,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    onDeleteReceipt: suspend (String, deleteReason: String) -> Unit,
    onSwitchEvidenceFailedToManual: suspend (String) -> Result<Unit>,
    onResendAnalysis: suspend (String) -> ReceiptRepository.ResendAnalysisResult,
    onOpenSettings: () -> Unit,
) {
    var loading by remember(receiptId) { mutableStateOf(true) }
    var receipt by remember(receiptId) { mutableStateOf<ReceiptEntity?>(null) }
    var items by remember(receiptId) { mutableStateOf<List<ReceiptItemEntity>>(emptyList()) }
    var image by remember(receiptId) { mutableStateOf<ReceiptImageEntity?>(null) }
    var geminiJson by remember(receiptId) { mutableStateOf<String?>(null) }
    var showGeminiJson by remember { mutableStateOf(false) }
    var showImageSheet by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResendConfirm by remember { mutableStateOf(false) }
    var resendMessage by remember { mutableStateOf<String?>(null) }
    var resendInFlight by remember { mutableStateOf(false) }
    var resendCooldownUntil by remember { mutableStateOf(0L) }
    var deleteReason by remember { mutableStateOf("MISTAKE") }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        val r = loadReceipt(receiptId)
        receipt = r
        if (r != null) {
            items = loadVisibleItems(receiptId)
            image = loadImage(receiptId)
            geminiJson = loadGeminiJson(receiptId)
        } else {
            items = emptyList()
            image = null
            geminiJson = null
        }
    }

    fun handleResendResult(result: ReceiptRepository.ResendAnalysisResult) {
        resendMessage = when (result) {
            ReceiptRepository.ResendAnalysisResult.Success ->
                "再送信しました。解析が完了するまでお待ちください。"
            ReceiptRepository.ResendAnalysisResult.ApiKeyMissing ->
                GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE
            ReceiptRepository.ResendAnalysisResult.ImageMissing ->
                "画像ファイルが見つかりません。再撮影するか削除してください。"
            ReceiptRepository.ResendAnalysisResult.AlreadyQueued ->
                "すでに解析待ちまたは処理中です。"
            ReceiptRepository.ResendAnalysisResult.NotFailed ->
                "解析失敗のレシートのみ再送信できます。"
            ReceiptRepository.ResendAnalysisResult.ReceiptNotFound ->
                "レシートが見つかりません。"
        }
        if (result == ReceiptRepository.ResendAnalysisResult.ApiKeyMissing) {
            onOpenSettings()
        }
    }

    LaunchedEffect(receiptId) {
        loading = true
        try {
            reload()
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

    val r = receipt
    if (r == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
        ) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("レシートが見つかりません。")
        }
        return
    }

    if (r.deletedAt != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
        ) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("このレシートは削除済みです。")
        }
        return
    }

    val missingHeaders = ReceiptRequiredFields.missingReceiptHeaders(r)
    val needsAttention = r.needsReview == 1 || r.analysisStatus == "FAILED" || r.analysisStatus == "NEEDS_REVIEW"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
            Text("レシート詳細", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("画像を表示") },
                        onClick = {
                            menuOpen = false
                            showImageSheet = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Gemini JSON") },
                        enabled = geminiJson != null,
                        onClick = {
                            menuOpen = false
                            showGeminiJson = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("修正") },
                        onClick = {
                            menuOpen = false
                            onOpenReview()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("削除") },
                        onClick = {
                            menuOpen = false
                            showDeleteDialog = true
                        },
                    )
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("レシートを削除") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("削除理由を選んでください（論理削除）。")
                        val opts = listOf(
                            "MISTAKE" to "誤送信",
                            "REFUND" to "返金/返品",
                            "OCR_MISS" to "OCRミス",
                            "DUPLICATE" to "重複",
                            "OTHER" to "その他",
                        )
                        opts.forEach { (code, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            ) {
                                RadioButton(selected = deleteReason == code, onClick = { deleteReason = code })
                                Text(label, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                onDeleteReceipt(receiptId, deleteReason)
                                showDeleteDialog = false
                                onBack()
                            }
                        },
                    ) { Text("削除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") }
                },
            )
        }

        if (showResendConfirm) {
            AlertDialog(
                onDismissRequest = { showResendConfirm = false },
                title = { Text("解析を再送信") },
                text = { Text("このレシートの解析をもう一度実行します。よろしいですか？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResendConfirm = false
                            scope.launch {
                                resendInFlight = true
                                try {
                                    val result = onResendAnalysis(receiptId)
                                    handleResendResult(result)
                                    if (result == ReceiptRepository.ResendAnalysisResult.Success) {
                                        reload()
                                    }
                                } finally {
                                    resendInFlight = false
                                    resendCooldownUntil = System.currentTimeMillis() + 3_000L
                                }
                            }
                        },
                    ) { Text("再送信") }
                },
                dismissButton = {
                    TextButton(onClick = { showResendConfirm = false }) { Text("キャンセル") }
                },
            )
        }

        resendMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { resendMessage = null },
                confirmButton = {
                    TextButton(onClick = { resendMessage = null }) { Text("OK") }
                },
                title = { Text("再送信") },
                text = { Text(msg) },
            )
        }

        if (showImageSheet && image != null) {
            AlertDialog(
                onDismissRequest = { showImageSheet = false },
                confirmButton = {
                    TextButton(onClick = { showImageSheet = false }) { Text("閉じる") }
                },
                title = { Text("レシート画像") },
                text = {
                    AsyncImage(
                        model = Uri.parse(image!!.localUri),
                        contentDescription = "Receipt image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                    )
                },
            )
        }

        if (showGeminiJson && geminiJson != null) {
            GeminiJsonViewerSheet(
                rawJson = geminiJson!!,
                onDismiss = { showGeminiJson = false },
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = formatIsoInstant(r.receiptDatetime ?: r.capturedAt),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .then(
                                    if (needsAttention && missingHeaders.any { it == "取引日時" }) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp)).padding(4.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Text(
                            text = r.merchantName?.ifBlank { "（店名なし）" } ?: "（店名なし）",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .then(
                                    if (needsAttention && missingHeaders.any { it == "店名" }) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp)).padding(4.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Text(
                            text = "合計 ${formatYen(r.totalAmountYen)}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .then(
                                    if (needsAttention && missingHeaders.any { it == "合計金額" }) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp)).padding(4.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Text(
                            text = "支払: ${PaymentMethodCatalog.label(r.paymentMethod)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .then(
                                    if (needsAttention && ReceiptRequiredFields.paymentMissing(r)) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp)).padding(4.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )

                        val kindLabel =
                            when (r.inputKind) {
                                "MANUAL_NO_RECEIPT" -> "手入力"
                                "EVIDENCE_IMAGE" -> "画像"
                                else -> null
                            }
                        if (kindLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    text = kindLabel,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            val statusDisplay = r.analysisStatusDisplay()
            if (statusDisplay.kind == AnalysisStatusKind.Failed ||
                statusDisplay.kind == AnalysisStatusKind.NeedsReview
            ) {
                item {
                    val resendEnabled =
                        !resendInFlight &&
                            System.currentTimeMillis() >= resendCooldownUntil &&
                            statusDisplay.kind == AnalysisStatusKind.Failed
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = statusDisplay.label,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            r.analysisErrorMessage?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            if (statusDisplay.kind == AnalysisStatusKind.Failed) {
                                Button(
                                    onClick = { showResendConfirm = true },
                                    enabled = resendEnabled,
                                ) {
                                    Text(if (resendInFlight) "送信中…" else "再送信")
                                }
                            }
                            if (statusDisplay.kind != AnalysisStatusKind.Failed) {
                                Button(onClick = onOpenReview) { Text("修正画面へ") }
                            } else {
                                Button(onClick = onOpenReview) { Text("手動で修正") }
                            }

                        if (statusDisplay.kind == AnalysisStatusKind.Failed && r.inputKind == "EVIDENCE_IMAGE") {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val result = onSwitchEvidenceFailedToManual(receiptId)
                                        if (result.isSuccess) {
                                            onOpenReview()
                                        }
                                    }
                                },
                            ) {
                                Text("手入力に切り替え")
                            }
                        }
                        }
                    }
                }
            } else if (statusDisplay.showBadge &&
                (statusDisplay.kind == AnalysisStatusKind.Pending ||
                    statusDisplay.kind == AnalysisStatusKind.Running)
            ) {
                item {
                    AnalysisStatusBadge(display = statusDisplay)
                }
            }

            item {
                Text("明細（調整行は表示しません）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        if (r.analysisStatus == "DONE" || r.analysisStatus == "NEEDS_REVIEW") {
                            "明細行がありません。"
                        } else {
                            "解析完了後に明細が表示されます。"
                        },
                    )
                }
            } else {
                items(items, key = { it.itemId }) { line ->
                    val lowConf = line.confidence < ReceiptRequiredFields.CONFIDENCE_THRESHOLD
                    val badCat = ReceiptRequiredFields.itemsWithInvalidCategory(listOf(line)).isNotEmpty()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (needsAttention && (lowConf || badCat)) {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.cardColors(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(line.itemName, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(formatYen(line.lineTotalYen), style = MaterialTheme.typography.bodyMedium)
                                Text("×${line.quantity}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "${CategoryCatalog.majorLabel(line.categoryMajor)} / ${line.categoryMinor}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text("必須度 ${line.necessityScore}　信頼度 ${"%.2f".format(line.confidence)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
