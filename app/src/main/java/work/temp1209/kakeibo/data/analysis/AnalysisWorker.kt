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

## 推論を許可する範囲（それ以外は印字の事実に従う）
次の3点のみ、画像からの解釈・推定を積極的に行ってよい。
1) セット・コンボ等を「1商品＝1行」に正規化し、itemName にオプション内容を織り込むこと
2) 型番・SKU・品番のみ印字の商品を、売場文脈から分かる日本語名へ書き換えること
3) 支払手段の表記を列挙値へ正規化し、カテゴリ（後述の major/minor **許可ペア**）を文脈から付与すること
上記以外（各金額・日付の数字・店名の主要表記など）は、レシートに読み取れる範囲で忠実に転記する。読めない箇所は捏造せず、confidence を下げ warnings に `[読取]` 等で記載する。

## 出力ルール（最重要）
- 出力は JSON のみ（前後に説明文/コードフェンス/注釈を付けない）
- スキーマに無いキーを追加しない（additionalProperties禁止）
- enum外の値を絶対に出さない
- 明細行(items)は「支出の明細行（商品）」のみ。値引き/割引/ポイント/クーポン等の行は items に出さない（warnings に `[値引]` 等で記載してよい）

## receipt ヘッダー
- merchantName: 店舗名として最も分かりやすい表記（チェーン名＋支店名が印字されていればそのまま近い形でよい）
- receiptDatetime: **必ず ISO 8601 形式**で、タイムゾーンは日本標準時を想定し末尾を **+09:00** とする。
  例: `2024-05-01T14:30:00+09:00`
  時刻が印字されない場合は `2024-05-01T00:00:00+09:00` とし、warnings に `[日時]` で「時刻不明」と短く書く。
  日付自体が判読不能な場合は、判読できる範囲で最善の日付を入れ confidence を下げ、warnings に `[日時]` を付ける（空文字は不可）。
- capturedAt: レシート画像内に「撮影日時」等が印字されている場合のみ同じ ISO 8601（+09:00）で入れる。なければ省略または空でよい（スキーマ上は任意）。
- totalAmountYen: 顧客が支払った **支払合計（税込が主なら税込）** を整数円で。印字に複数の「合計」がある場合は、**領収・会計として最終の支払額**を優先する。
- paymentMethod: 次の対応で列挙値に正規化する。判断できなければ **UNKNOWN**。
  - 現金のみ / CASH / 現金 → CASH
  - クレジット / クレジット売上 / VISA JCB MASTER 等のカード端末表記 → CREDIT_CARD
  - デビット → DEBIT_CARD
  - Suica PASMO ICOCA 等の交通系 → TRANSPORT_IC
  - WAON nanaco 楽天Edy QuickPay 等の汎用電子マネー → E_MONEY
  - PayPay LINE Pay メルペイ d払い 楽天ペイ 等のQR・コード決済 → QR_PAYMENT
  - 口座振替・引落し表記 → BANK_TRANSFER_OR_DEBIT
- paymentServiceName: 上記に加え、ブランド名（例: PayPay、VISA）が分かれば短く入れる。不要なら空でよい。

## lineIndex（明細行ごと）
- **0 起算の整数**。レシート上の「items に残す商品明細」を、**上から読める順**に 0,1,2,… と振る。
- セット統合で複数印字行を1行にまとめた場合は **1つの lineIndex** にまとめ、欠番が出てもよい（同一レシート内で **重複禁止**）。

## セット・コンボ・カスタム行の扱い（重要）
- バーガーセット、定食セット、ドリンク＋サイド付きなど **セット・コンボは items に1行だけ** 出力する。
  - itemName には、サイドの大きさ・ドリンク種・主要オプションを括弧や読点で織り込む（例: ハンバーガーセット（ポテトL、コーラM））
  - lineTotalYen はレシート上でそのセットに対応する **セット価格（その行の支払額）** を使う。quantity は通常 1。
- **禁止**: セットを分解して、次のような **付随行・差額だけの明細を別行にしない**
  - Lサイズアップ +50円、Sに変更 -20円 など **サイズ変更の差額だけの行**
  - 氷抜き 0円、トッピングなし 0円、変更なし 0円 など **0円のオプション説明行**
  - 本体のセット行があるのに、それに従属する注釈・内訳だけの行
- レシートがセットを複数行に分けて印字していても、家計簿上は **1商品＝1行** に正規化する。金額はセットとして支払った合計をその1行の lineTotalYen に載せ、必要なら warnings に `[統合]` と短く書く。
- レシート上で **別料金の追加商品**（単品ドリンク、追加パティ等）として独立している行は、従来どおり別の明細行としてよい。

## itemName（型番・品番のみのとき）
- 商品名が **型番・SKU・品番だけ**（例: SKBM-6039、ABC-123-X）のように **一般名が印字されていない** 場合、itemName は **家計簿で意味が通じる具体的な日本語名** に書き換える。
  - レシート上の **店名・フロア・売場・カテゴリ見出し・近傍の説明文・JAN付近の表記** から推定し、「電気ケトル」「長袖Tシャツ」「USBケーブル」などユーザーが後から見て分かる表現にする。
  - 推定に自信がある場合は型番は括弧で添えてよい（例: 電気ケトル（SKBM-6039））。推定が弱い場合は **ジャンル＋型番**（例: 家電・小物（SKBM-6039））のようにし、**型番のみ**の itemName にしない。
  - 推測に大きく依存する場合は confidence を下げ、warnings に `[推定]` を付ける。

## category（major と minor の許可ペアのみ。ここを外すと無効）
categoryMinor は **その行の categoryMajor に対応する右列のいずれかだけ** を選ぶ（他 major のラベルは禁止）。
- FOOD → スーパー / 外食 / その他
- DAILY_GOODS → 衛生用品 / 雑貨（小物） / 家具家電 / その他
- TRANSPORT → 電車/バス / 定期券 / その他
- HOUSING → 家賃/住宅ローン / 家具（大物） / その他
- UTILITIES → 電気 / ガス / 水道 / その他
- COMMUNICATION → 携帯 / インターネット / サブスク（通信系） / その他
- MEDICAL → 病院 / 薬 / 健康/検診 / その他
- EDUCATION → 教材 / その他
- SOCIAL → 交際費 / 飲み会 / 贈り物 / 冠婚葬祭 / その他
- ENTERTAINMENT → 趣味 / 旅行 / イベント / ゲーム（買い切り） / ゲーム（課金） / その他
- CLOTHING → 衣類 / 靴/バッグ / クリーニング / その他
- OTHER → 返済 / その他

## confidence（0.0〜1.0、明細行ごと）
「その明細行の itemName / quantity / lineTotalYen / categoryMajor / categoryMinor / necessityScore が事実・意図どおりである主観確率」。
- **同一レシート内で較正する**: 最も読み取りが確実な行を基準に、他行はそれより下げる。同じ画質なのに行だけ極端に高い confidence を付けない。
- 商品名・金額の数字がはっきり読めている行は、原則 **0.70 未満にしない**（分類だけ迷う場合は分類の不確実さで 0.70〜0.85 に落とす）。
- 0.90〜1.00: 文字も金額も明瞭で、分類もほぼ確実
- 0.70〜0.89: 一部推測だが整合的
- 0.40〜0.69: 商品名/金額/分類のいずれかが曖昧で推測に依存
- 0.00〜0.39: ほぼ読めない/推測が強い（itemName="不明" を使ってよい）
曖昧な場合は高くしないが、「同一画像内の相対」は保つこと。

## necessityScore（0〜100、明細行ごと）
目的: 一般家庭の家計における **無駄遣い（裁量支出）可視化**。道徳判断ではなく「生活維持への必要性」の目安とする（法人経費レシートでも同じスケールでよい）。
- 100に近いほど生活必須、0に近いほど娯楽・裁量
- 90〜100: 食料品・日用品・処方薬など明確な必需品
- 60〜89: 弁当・惣菜・外食全般、生理用品、軽微な嗜好を伴う日用品
- 30〜59: スイーツ・カフェ飲料・お菓子・趣味雑貨寄り
- 0〜29: ゲーム課金、高単価趣味、明確な娯楽・贅沢寄り
- サプリ・化粧品は **健康/美容目的の裁量**として 30〜70 の幅を使い分ける（医薬品の「薬」は上記に近い）
商品が不明で判断できない場合は **50** に寄せ、confidence を下げる。

## 金額・税・合計
- quantity が取れない場合は 1
- lineTotalYen: 印字されている **その商品行の支払額（通常は税込の行合計）** を整数円。税抜のみの行と税込合計が混在するレシートでは、**印字に従い** items を作り、合計との差はアプリ側で調整され得る旨を踏まえ、無理に行を作り替えない。
- totalAmountYen: 上記 receipt の支払合計と同じ考え方。
- items の lineTotalYen の和が totalAmountYen と一致しない場合（端数・値引の別欄・内税表記など）は、**印字値を優先**し、warnings に `[金額]` で要因を短く書く（明細を捏造して合わせない）。

## warnings
- 日本語で簡潔に。先頭に次のいずれかの **タグ** を推奨: `[読取]` `[日時]` `[金額]` `[値引]` `[統合]` `[推定]` `[税込]`
- **最大 8 件**、各 **80 文字以内**。空配列でもよい。
"""
    }
}

