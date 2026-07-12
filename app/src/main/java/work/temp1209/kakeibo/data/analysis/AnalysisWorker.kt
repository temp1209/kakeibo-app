package work.temp1209.kakeibo.data.analysis

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import work.temp1209.kakeibo.data.analysis.model.ReceiptItem
import work.temp1209.kakeibo.data.db.AnalysisQueueEntity
import work.temp1209.kakeibo.data.db.GeminiResultEntity
import work.temp1209.kakeibo.data.db.ReceiptDao
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.gemini.GeminiResponseParser
import work.temp1209.kakeibo.data.gemini.GeminiUserMessages
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.data.prefs.NecessityPolicyStore
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.notifications.NotificationHistory
import work.temp1209.kakeibo.ui.notifications.AnalysisNotifications
import java.time.Instant
import java.util.UUID

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).receiptDao()
        val gemini = GeminiClient()

        Log.d(TAG, "doWork start")
        while (true) {
            val entry = dao.getNextQueuedOrNull() ?: break
            val apiKey = GeminiApiKeyStore(applicationContext).readKeyOrNull()
            if (apiKey == null) {
                handleMissingApiKeyFailure(dao = dao, entry = entry)
                continue
            }

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

                val prompt = buildPrompt(applicationContext)
                val rawResponse = gemini.generateStrictJsonFromImage(
                    apiKey = apiKey,
                    jpegBytes = jpegBytes,
                    prompt = prompt,
                    responseJsonSchema = ReceiptJsonSchema.schemaV1(),
                )

                val strictJson = extractStrictJson(rawResponse)
                val parsed = try {
                    GeminiStrictParser.parseStrictJson(strictJson)
                } catch (_: NoReceiptInImageException) {
                    handleNoReceiptFailure(
                        dao = dao,
                        entry = entry,
                        strictJson = strictJson,
                        now = now,
                        attempt = attempt,
                    )
                    continue
                }
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
                val updatedReceipt = existing.copy(
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
                dao.upsertReceipt(updatedReceipt)

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
                recordOutcomeAndMaybeNotify(
                    receipt = updatedReceipt,
                    eventType = if (flags2.needsReview) {
                        NotificationHistory.TYPE_NEEDS_REVIEW
                    } else {
                        NotificationHistory.TYPE_DONE
                    },
                    notify = {
                        if (flags2.needsReview) {
                            AnalysisNotifications.notifyNeedsReview(applicationContext, entry.receiptId)
                        } else {
                            AnalysisNotifications.notifyDone(applicationContext, entry.receiptId)
                        }
                    },
                    notifyGateReceipt = existing,
                )
                Log.d(TAG, "done receiptId=${entry.receiptId} status=$receiptStatus subtotal=$subtotal adjustment=$adjustment")
            } catch (e: Exception) {
                val msg = GeminiUserMessages.userFacingError(e, GeminiUserMessages.Operation.RECEIPT_ANALYSIS)
                Log.w(TAG, "failed receiptId=${entry.receiptId} attempt=$attempt error=$msg", e)
                val finishedAt = Instant.now().toString()
                val existing = dao.getReceiptOrNull(entry.receiptId)
                val failedReceipt = existing?.copy(
                    analysisStatus = "FAILED",
                    analysisStartedAt = existing.analysisStartedAt ?: now,
                    analysisCompletedAt = finishedAt,
                    analysisErrorMessage = msg,
                    needsReview = 1,
                    updatedAt = finishedAt,
                )
                if (failedReceipt != null) {
                    dao.upsertReceipt(failedReceipt)
                }
                dao.finishQueue(
                    queueId = entry.queueId,
                    status = "FAILED",
                    finishedAt = finishedAt,
                    lastError = msg,
                    attemptCount = attempt,
                )
                if (failedReceipt != null) {
                    recordOutcomeAndMaybeNotify(
                        receipt = failedReceipt,
                        eventType = NotificationHistory.TYPE_FAILED,
                        notify = { AnalysisNotifications.notifyFailed(applicationContext, entry.receiptId) },
                        notifyGateReceipt = existing,
                    )
                }
            }
        }

        Log.d(TAG, "doWork end (no more queued)")
        return Result.success()
    }

    private suspend fun handleMissingApiKeyFailure(
        dao: ReceiptDao,
        entry: AnalysisQueueEntity,
    ) {
        val now = Instant.now().toString()
        val attempt = entry.attemptCount + 1
        val message = GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE
        Log.w(TAG, "api key missing receiptId=${entry.receiptId}")
        dao.markQueueRunning(queueId = entry.queueId, startedAt = now)
        val existing = dao.getReceiptOrNull(entry.receiptId)
        val failedReceipt = existing?.copy(
            analysisStatus = "FAILED",
            analysisStartedAt = existing.analysisStartedAt ?: now,
            analysisCompletedAt = now,
            analysisErrorMessage = message,
            needsReview = 1,
            updatedAt = now,
        )
        if (failedReceipt != null) {
            dao.upsertReceipt(failedReceipt)
        }
        dao.finishQueue(
            queueId = entry.queueId,
            status = "FAILED",
            finishedAt = now,
            lastError = message,
            attemptCount = attempt,
        )
        if (failedReceipt != null) {
            recordOutcomeAndMaybeNotify(
                receipt = failedReceipt,
                eventType = NotificationHistory.TYPE_FAILED,
                notify = { AnalysisNotifications.notifyFailed(applicationContext, entry.receiptId) },
                notifyGateReceipt = existing,
            )
        }
    }

    /**
     * モデルが `[NO_RECEIPT]` を返した場合: リトライせず解析失敗として確定する（生JSONは保存）。
     */
    private suspend fun handleNoReceiptFailure(
        dao: ReceiptDao,
        entry: AnalysisQueueEntity,
        strictJson: String,
        now: String,
        attempt: Int,
    ) {
        val finishedAt = Instant.now().toString()
        val receiptId = entry.receiptId
        val existing = dao.getReceiptOrNull(receiptId) ?: return
        val message = "画像にレシートが見当たりません。別の画像で試してください。"
        dao.insertGeminiResult(
            GeminiResultEntity(
                resultId = UUID.randomUUID().toString(),
                receiptId = receiptId,
                schemaVersion = "1.0",
                model = "gemini-2.5-flash",
                rawJson = strictJson,
                createdAt = finishedAt,
            ),
        )
        val failedReceipt = existing.copy(
            analysisStatus = "FAILED",
            analysisStartedAt = existing.analysisStartedAt ?: now,
            analysisCompletedAt = finishedAt,
            analysisErrorMessage = message,
            needsReview = 1,
            updatedAt = finishedAt,
        )
        dao.upsertReceipt(failedReceipt)
        dao.finishQueue(
            queueId = entry.queueId,
            status = "FAILED",
            finishedAt = finishedAt,
            lastError = message,
            attemptCount = attempt,
        )
        recordOutcomeAndMaybeNotify(
            receipt = failedReceipt,
            eventType = NotificationHistory.TYPE_FAILED,
            notify = { AnalysisNotifications.notifyFailed(applicationContext, receiptId) },
            notifyGateReceipt = existing,
        )
        Log.w(TAG, "no receipt in image receiptId=$receiptId")
    }

    private suspend fun recordOutcomeAndMaybeNotify(
        receipt: ReceiptEntity,
        eventType: String,
        notify: () -> Unit,
        notifyGateReceipt: ReceiptEntity?,
    ) {
        NotificationHistory.record(applicationContext, receipt, eventType)
        if (shouldSendAnalysisOsNotification(notifyGateReceipt)) {
            notify()
        }
    }

    private fun extractStrictJson(rawResponse: String): String =
        GeminiResponseParser.extractResponseText(rawResponse)

    companion object {
        /**
         * 要件 P6-7: `MANUAL_NO_RECEIPT` では解析完了の OS 通知を出さない（キュー非投入が前提だが、万一の保険）。
         * `RECEIPT_CAMERA` / `EVIDENCE_IMAGE` は従来どおり。
         */
        private fun shouldSendAnalysisOsNotification(receipt: ReceiptEntity?): Boolean =
            receipt?.inputKind != "MANUAL_NO_RECEIPT"

        private const val TAG = "AnalysisWorker"

        private fun buildPrompt(context: Context): String {
            val userBlock = NecessityPolicyStore(context.applicationContext).getEffectivePromptBlock()
            val marker = "（例: FOOD/外食でもスイーツ単品は低め）。\n\n### スコア帯の目安"
            val injection = "（例: FOOD/外食でもスイーツ単品は低め）。\n\n### ユーザー向け必須度方針\n$userBlock\n\n### スコア帯の目安"
            return PROMPT.replace(marker, injection)
        }

        private const val PROMPT = """
あなたは家計簿アプリのOCR/分類エンジンです。入力は **レシートの写真**（紙・電子レシートの表示を撮影したもの）に限らず、**レシートと同等の購入・会計情報が写っているスクリーンショット**（スマホ・PC の画面キャプチャ）も受け取ります。いずれも「購入を示す伝票・明細・合計・店名・日付」が読み取れる範囲で、同じルールで抽出し、必ず「指定スキーマの厳格JSONのみ」を返してください。

## 入力画像の種別（写真とスクリーンショット）
- **レシート写真**: 店頭の紙レシート、レシート端末の印字、電子レシートを別端末で撮影したものなど。
- **スクリーンショット**: 決済アプリの完了画面、EC の注文確認・領収画面、メールアプリ内の領収メール本文、PDFビューア上の領収書、自販機・キオスクの購入完了UI など、**レシートと同等の情報**（店舗名・購入日時・支払合計・商品行など）が画面内に含まれる画像。
- ステータスバー・ナビゲーションバー・通知アイコン・余白のUI枠は無視し、**会計・購入に関する本文・表・金額**を読み取る対象とする。スクショでも上記が判読できれば通常どおり `items` と `receipt` を埋める。

## 推論を許可する範囲（それ以外は印字の事実に従う）
次の3点のみ、画像からの解釈・推定を積極的に行ってよい。
1) セット・コンボ等を「1商品＝1行」に正規化し、itemName にオプション内容を織り込むこと
2) 型番・SKU・品番のみ印字の商品を、売場文脈から分かる日本語名へ書き換えること
3) 支払手段の表記を列挙値へ正規化し、カテゴリ（後述の major/minor **許可ペア**）を文脈から付与すること
上記以外（各金額・日付の数字・店名の主要表記など）は、レシートに読み取れる範囲で忠実に転記する。読めない箇所は捏造せず、confidence を下げ warnings に `[読取]` 等で記載する。

## レシート相当の情報が画像にないとき（最重要）
次のいずれかに該当し、**写真・スクリーンショットを問わず**、**会計上の販売レシート・領収・決済完了表示（購入の合計・店名・日付が読み取れる伝票相当）として合理的に復元できない**と判断した場合:
- 何も写っていない／真っ暗・壁・床・無関係な物体のみ
- レシートではない書類（チラシ、メモ、配送伝票のみ等）だけで、購入の会計体裁がない
- **スクリーンショットだが**、ホーム画面・アプリ一覧・SNSのタイムライン・無関係なWeb記事・ゲーム画面など、**購入・決済・領収に関する内容が写っていない**もの
- レシートの断片のみで店名・合計・日付のいずれも確信できず、推測で埋めると誤記録になる

このとき **通常の抽出結果を捏造してはならない**。代わりに次を **すべて**満たす厳格JSONのみを返すこと:
1) `warnings` に **`[NO_RECEIPT]` と完全一致する文字列** を **1要素以上** 含める（他の警告文字列と併用可）
2) `items` は **空配列 `[]`**
3) `receipt.merchantName` は **`不明`**
4) `receipt.totalAmountYen` は **0**
5) `receipt.receiptDatetime` は **`1970-01-01T00:00:00+09:00`**（プレースホルダ。実取引の日時として使わない）
6) `receipt.paymentMethod` は **UNKNOWN** か省略（可能なら UNKNOWN）

【禁止】レシートが無いのに、見込みで店名・金額・明細を創作すること。

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
- capturedAt: 入力画像（紙レシート印字・画面表示のいずれか）に「撮影日時」「表示日時」等が読める場合のみ同じ ISO 8601（+09:00）で入れる。なければ省略または空でよい（スキーマ上は任意）。
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
- **集計の二値化境界は 50**（≥50=必須寄り、<50=裁量寄り）。境界付近は ±5 点の揺れを許容しつつ、下記の帯を優先する

### 判定の最重要原則（店種別ルール禁止）
- **商品そのものの用途・性質**でスコアを決める。**店舗種別だけで一括判定してはならない**。
  - コンビニでも「弁当・おにぎり」は生活食、「スイーツ・菓子・嗜好飲料」は裁量寄り
  - 100均でも「消耗品・掃除用品」は必需品寄り、「玩具・装飾雑貨」は裁量寄り
  - スーパーでも「スイーツ専売コーナーの菓子」は裁量寄りに下げる
- カテゴリ（categoryMajor/Minor）は分類用。necessityScore は **カテゴリと独立に、用途で上書きしてよい**（例: FOOD/外食でもスイーツ単品は低め）。

### スコア帯の目安
- 90〜100: 食料品（食材）・処方薬・生理用品・洗剤等の明確な生活必需品
- 70〜89: スーパー惣菜、コンビニ弁当・おにぎり、定期の通信費・光熱の実費、通勤交通
- 55〜69: 外食全般（ランチ・定食）、消耗する日用品（雑貨だが生活実用）、カフェの食事寄りメニュー
- 35〜54: スイーツ・お菓子・カフェ飲料単体、美容・サプリ（健康目的の裁量）、100均の装飾雑貨
- 15〜34: 趣味雑貨・コレクション、娯楽イベント、ゲーム買い切り（高額趣味寄りはさらに下げてよい）
- 0〜14: ゲーム課金・ガチャ、明確な贅沢品・高額趣味、非生活の衝動買い

### 境界ケースの具体例（この帯を優先）
| 商品例 | 目安スコア | 理由 |
|--------|-----------|------|
| コンビニ弁当・おにぎり・惣菜 | 65〜80 | 生活食・外食の実用 |
| コンビニスイーツ・菓子・エナドリ（嗜好） | 25〜45 | 嗜好・甘味中心 |
| 100均: テープ・電池・洗剤・ゴミ袋 | 75〜90 | 生活消耗品 |
| 100均: 玩具・ステッカー・装飾小物 | 20〜40 | 趣味・衝動買い寄り |
| 100均: 収納ボックス・ハンガー（実用） | 55〜75 | 生活整理の実用品 |
| 携帯・インターネット・通信系サブスク | 70〜88 | 生活インフラ |
| ゲーム課金・ガチャ | 5〜20 | 明確な裁量娯楽 |
| スーパー食材（野菜・肉・米） | 88〜100 | 食費の必需品 |
| カフェコーヒー単体（食事なし） | 35〜50 | 嗜好飲料 |

- サプリ・化粧品は **健康/美容目的の裁量**として 30〜65。医薬品の「薬」（OTC含む）は 80〜95
- 同一レシート内で商品を比較し、必需品と嗜好品の **相対差**がスコアに反映されていること（全行が70前後に固まらない）
- 商品が不明で判断できない場合は **50** に寄せ、confidence を下げる

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

