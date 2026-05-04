package work.temp1209.kakeibo.data.analysis

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import work.temp1209.kakeibo.data.analysis.model.ReceiptItem
import work.temp1209.kakeibo.data.db.GeminiResultEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.ui.notifications.AnalysisNotifications
import java.time.Instant
import java.util.UUID

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).receiptDao()
        val apiKey = GeminiApiKeyStore(applicationContext).readKeyOrNull() ?: return Result.success()
        val gemini = GeminiClient()

        Log.d(TAG, "doWork start")
        while (true) {
            val entry = dao.getNextQueuedOrNull() ?: break
            val now = Instant.now().toString()
            Log.d(TAG, "dequeue queueId=${entry.queueId} receiptId=${entry.receiptId} attemptCount=${entry.attemptCount}")
            dao.markQueueRunning(queueId = entry.queueId, startedAt = now)

            val attempt = entry.attemptCount + 1
            val head = dao.getReceiptOrNull(entry.receiptId)
            if (head?.deletedAt != null) {
                val skipAt = Instant.now().toString()
                Log.d(TAG, "skip queue: receipt deleted receiptId=${entry.receiptId}")
                dao.finishQueue(
                    queueId = entry.queueId,
                    status = "DONE",
                    finishedAt = skipAt,
                    lastError = null,
                    attemptCount = attempt,
                )
                continue
            }

            try {
                val img = dao.getReceiptImage(entry.receiptId) ?: error("receipt image missing")
                val jpegBytes = applicationContext.contentResolver.openInputStream(android.net.Uri.parse(img.localUri))
                    ?.use { it.readBytes() }
                    ?: error("failed to read image")
                Log.d(TAG, "loaded image bytes=${jpegBytes.size}")

                val rawResponse = gemini.generateStrictJsonFromImage(
                    apiKey = apiKey,
                    jpegBytes = jpegBytes,
                    prompt = PROMPT,
                    responseJsonSchema = ReceiptJsonSchema.schemaV1(),
                )

                val strictJson = extractStrictJson(rawResponse)
                val parsed = GeminiStrictParser.parseStrictJson(strictJson)
                val flags = GeminiStrictParser.reviewFlags(parsed, confidenceThreshold = 0.7)
                Log.d(TAG, "parsed strictJson items=${parsed.items.size} needsReview=${flags.needsReview}")

                val merchantName = parsed.receipt.merchantName
                val receiptDatetime = parsed.receipt.receiptDatetime
                val totalAmountYen = parsed.receipt.totalAmountYen
                val paymentMethod = parsed.receipt.paymentMethod
                val paymentServiceName = parsed.receipt.paymentServiceName

                val items = mutableListOf<ReceiptItemEntity>()
                var subtotal = 0L
                val seenLineIndex = hashSetOf<Int>()
                val normalizedItems = parsed.items
                    .sortedBy { it.lineIndex }
                    .filter { it.lineIndex >= 0 }
                    .filter { seenLineIndex.add(it.lineIndex) }

                val dropped = parsed.items.size - normalizedItems.size
                val flags2 = if (dropped > 0) {
                    flags.copy(needsReview = true, reasons = (flags.reasons + "duplicate/invalid lineIndex dropped: $dropped").distinct())
                } else {
                    flags
                }

                for (it: ReceiptItem in normalizedItems) {
                    subtotal += it.lineTotalYen
                    items += ReceiptItemEntity(
                        itemId = "${entry.receiptId}:${it.lineIndex}",
                        receiptId = entry.receiptId,
                        lineIndex = it.lineIndex,
                        itemName = it.itemName,
                        quantity = it.quantity,
                        lineTotalYen = it.lineTotalYen,
                        categoryMajor = it.categoryMajor,
                        categoryMinor = it.categoryMinor,
                        necessityScore = it.necessityScore,
                        confidence = it.confidence,
                        isAdjustment = 0,
                    )
                }

                val adjustment = totalAmountYen - subtotal
                if (adjustment != 0L) {
                    val maxLineIndex = normalizedItems.maxOfOrNull { it.lineIndex } ?: -1
                    items += ReceiptItemEntity(
                        itemId = "${entry.receiptId}:adjustment",
                        receiptId = entry.receiptId,
                        lineIndex = maxLineIndex + 1,
                        itemName = "調整",
                        quantity = 1,
                        lineTotalYen = adjustment,
                        categoryMajor = "OTHER",
                        categoryMinor = "その他",
                        necessityScore = 50,
                        confidence = 1.0,
                        isAdjustment = 1,
                    )
                }

                val existing = dao.getReceiptOrNull(entry.receiptId) ?: error("receipt missing")
                val updatedAt = Instant.now().toString()
                val receiptStatus = if (flags2.needsReview) "NEEDS_REVIEW" else "DONE"
                val errorMessage = if (flags2.needsReview) flags2.reasons.joinToString("; ") else null
                dao.upsertReceipt(
                    existing.copy(
                        receiptDatetime = receiptDatetime,
                        merchantName = merchantName,
                        totalAmountYen = totalAmountYen,
                        paymentMethod = paymentMethod,
                        paymentServiceName = paymentServiceName,
                        analysisStatus = receiptStatus,
                        analysisStartedAt = now,
                        analysisCompletedAt = updatedAt,
                        analysisErrorMessage = errorMessage,
                        needsReview = if (flags2.needsReview) 1 else 0,
                        itemsSubtotalYen = subtotal,
                        adjustmentYen = adjustment,
                        updatedAt = updatedAt,
                    )
                )

                dao.upsertReceiptItems(items)
                dao.insertGeminiResult(
                    GeminiResultEntity(
                        resultId = UUID.randomUUID().toString(),
                        receiptId = entry.receiptId,
                        schemaVersion = parsed.schemaVersion,
                        model = "gemini-2.5-flash",
                        rawJson = strictJson,
                        createdAt = updatedAt,
                    )
                )

                dao.finishQueue(
                    queueId = entry.queueId,
                    status = "DONE",
                    finishedAt = updatedAt,
                    lastError = null,
                    attemptCount = attempt,
                )
                if (flags2.needsReview) {
                    AnalysisNotifications.notifyFailed(applicationContext, entry.receiptId)
                } else {
                    AnalysisNotifications.notifyDone(applicationContext, entry.receiptId)
                }
                Log.d(TAG, "done receiptId=${entry.receiptId} status=$receiptStatus subtotal=$subtotal adjustment=$adjustment")
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "failed receiptId=${entry.receiptId} attempt=$attempt error=$msg", e)
                if (attempt <= 1) {
                    // Put back to QUEUED for a single retry with WorkManager backoff.
                    dao.requeue(entry.queueId, attemptCount = attempt, lastError = msg)
                    return Result.retry()
                } else {
                    val finishedAt = Instant.now().toString()
                    val existing = dao.getReceiptOrNull(entry.receiptId)
                    if (existing != null) {
                        dao.upsertReceipt(
                            existing.copy(
                                analysisStatus = "FAILED",
                                analysisStartedAt = existing.analysisStartedAt ?: now,
                                analysisCompletedAt = finishedAt,
                                analysisErrorMessage = msg,
                                needsReview = 1,
                                updatedAt = finishedAt,
                            )
                        )
                    }
                    dao.finishQueue(entry.queueId, status = "FAILED", finishedAt = finishedAt, lastError = msg, attemptCount = attempt)
                    AnalysisNotifications.notifyFailed(applicationContext, entry.receiptId)
                }
            }
        }

        Log.d(TAG, "doWork end (no more queued)")
        return Result.success()
    }

    private fun extractStrictJson(rawResponse: String): String {
        // generateContent response: candidates[0].content.parts[0].text
        val root = JSONObject(rawResponse)
        val candidates = root.getJSONArray("candidates")
        if (candidates.length() == 0) error("no candidates")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        if (parts.length() == 0) error("no parts")
        val text = parts.getJSONObject(0).optString("text")
        if (text.isBlank()) error("empty response text")
        return text.trim()
    }

    companion object {
        private const val TAG = "AnalysisWorker"
        private const val PROMPT = """
あなたは家計簿アプリのOCR/分類エンジンです。レシート画像から情報を抽出し、必ず「指定スキーマの厳格JSONのみ」を返してください。

## 出力ルール（最重要）
- 出力は JSON のみ（前後に説明文/コードフェンス/注釈を付けない）
- スキーマに無いキーを追加しない（additionalProperties禁止）
- enum外の値を絶対に出さない（例: categoryMajor に "Discount" などを作らない）
- 明細行(items)は「支出の明細行（商品）」のみ。値引き/割引/ポイント/クーポン等の行は items に出さない（warnings に記載して良い）

## セット・コンボ・カスタム行の扱い（重要）
- バーガーセット、定食セット、ドリンク＋サイド付きなど **セット・コンボは items に1行だけ** 出力する。
  - itemName には、サイドの大きさ・ドリンク種・主要オプションを括弧や読点で織り込む（例: ハンバーガーセット（ポテトL、コーラM））
  - lineTotalYen はレシート上でそのセットに対応する **セット価格（その行の支払額）** を使う。quantity は通常 1。
- **禁止**: セットを分解して、次のような **付随行・差額だけの明細を別行にしない**
  - Lサイズアップ +50円、Sに変更 -20円 など **サイズ変更の差額だけの行**
  - 氷抜き 0円、トッピングなし 0円、変更なし 0円 など **0円のオプション説明行**
  - 本体のセット行があるのに、それに従属する注釈・内訳だけの行
- レシートがセットを複数行に分けて印字していても、家計簿上は **1商品＝1行** に正規化する。金額はセットとして支払った合計をその1行の lineTotalYen に載せ、必要なら warnings に「複数行をセット1件に統合」と短く書く。
- レシート上で **別料金の追加商品**（単品ドリンク、追加パティ等）として独立している行は、従来どおり別の明細行としてよい。

## itemName（型番・品番のみのとき）
- 商品名が **型番・SKU・品番だけ**（例: SKBM-6039、ABC-123-X）のように **一般名が印字されていない** 場合、itemName は **家計簿で意味が通じる具体的な日本語名** に書き換える。
  - レシート上の **店名・フロア・売場・カテゴリ見出し・近傍の説明文・JAN付近の表記** から推定し、「電気ケトル」「長袖Tシャツ」「USBケーブル」などユーザーが後から見て分かる表現にする。
  - 推定に自信がある場合は型番は括弧で添えてよい（例: 電気ケトル（SKBM-6039））。推定が弱い場合は **ジャンル＋型番**（例: 家電・小物（SKBM-6039））のようにし、**型番のみ**の itemName にしない。
  - 推測に大きく依存する場合は confidence を下げ、必要なら warnings に「品番からの推定」と短く書く。

## category（固定リスト、表記ゆれ禁止）
- categoryMajor は次のいずれか: FOOD / DAILY_GOODS / TRANSPORT / HOUSING / UTILITIES / COMMUNICATION / MEDICAL / EDUCATION / SOCIAL / ENTERTAINMENT / CLOTHING / OTHER
- categoryMinor は次の日本語ラベルのいずれかに完全一致:
  スーパー, 外食, その他,
  衛生用品, 雑貨（小物）, 家具家電,
  電車/バス, 定期券,
  家賃/住宅ローン, 家具（大物）,
  電気, ガス, 水道,
  携帯, インターネット, サブスク（通信系）,
  病院, 薬, 健康/検診,
  教材,
  交際費, 飲み会, 贈り物, 冠婚葬祭,
  趣味, 旅行, イベント, ゲーム（買い切り）, ゲーム（課金）,
  衣類, 靴/バッグ, クリーニング,
  返済

## confidence（0.0〜1.0）の定義（明細行ごと）
「その明細行(itemName/quantity/lineTotalYen/categoryMajor/categoryMinor/necessityScore)が正しい確率」の自己評価。
- 0.90〜1.00: 文字も金額も明瞭で、分類もほぼ確実
- 0.70〜0.89: 多少不鮮明だが推測可能（分類も妥当）
- 0.40〜0.69: 商品名/金額/分類のいずれかが曖昧。推測に依存
- 0.00〜0.39: ほぼ読めない/推測が強い（itemName="不明" を使ってよい）
曖昧な場合は「高くしない」。迷ったら低めに。

## necessityScore（0〜100）の定義（明細行ごと）
目的: 無駄遣い（裁量支出）可視化のためのスコア。
- 100に近いほど「生活必須（必需品）」、0に近いほど「娯楽/裁量」
例:
- 90〜100: 食材/日常消耗品/医薬品など
- 60〜89: 生活に必要だが代替の幅があるもの（外食、日用品の一部など）
- 30〜59: 嗜好品/趣味寄り
- 0〜29: 明確に娯楽・贅沢（ゲーム課金、趣味性が強いもの等）
商品が不明で判断できない場合は 50 に寄せ、confidence を下げる。

## 金額/数量
- quantity が取れない場合は 1
- lineTotalYen は「その明細行の行合計（円）」を整数で
- totalAmountYen は「レシート合計（円）」を整数で

## warnings
読み取りが怪しい/割引行がある/不明点がある場合は warnings に日本語で簡潔に記載。
"""
    }
}

