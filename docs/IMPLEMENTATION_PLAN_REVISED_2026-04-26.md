# 実装計画（改訂版） 2026-04-26

このファイルは、現状実装（Phase2完了相当）を前提に、`docs/REQUIREMENTS.md` と整合する形へ計画を整理し直したもの。

---

## 現状の到達点（要約）
- **Phase1**: 起動=即カメラ、プレビュー確定でローカル保存、一覧/詳細の最小、40日掃除（起動時）
- **Phase2**: APIキー安全保管、Gemini(REST+OkHttp)で厳格JSON、解析キュー(WorkManager/1件ずつ)、`gemini_results` 保存、通知（DONE/FAILED）、通知タブ（キュー/最終エラー/要確認入口/最近の解析結果）、Gemini JSON表示（レシート詳細）

---

## Phase 2（完了条件の再定義）

> 目標: **送信確定 → 即キュー投入 → Gemini解析 → DBへ反映 → 低信頼は要確認へ**

### 2.1 設定（APIキー）
- [x] APIキー入力/更新/削除
- [x] 安全保管（EncryptedSharedPreferences）
- [x] 疎通テスト（テキスト）

### 2.2 Gemini呼び出し
- [x] REST + OkHttp
- [x] 画像入力 + 厳格JSON（responseMimeType + responseJsonSchema）
- [x] タイムアウト/ネットワーク制約（WorkManager: CONNECTED）

### 2.3 厳格パース/バリデーション
- [x] データクラス化（strict JSON → model）
- [x] 低信頼判定（confidence < 0.7）と needsReview フラグ
- [x] lineIndex重複/不正の検出と保守的な保存（衝突回避）
- [x] categoryMajor と categoryMinor の対応関係バリデーション（解析時 + 修正画面）

### 2.4 解析キュー（WorkManager）
- [x] `analysis_queue`（QUEUED/RUNNING/DONE/FAILED）
- [x] 重複防止
- [x] 1件ずつ処理
- [x] リトライ1回（合計2トライ）

### 2.5 通知/運用ハブ
- [x] OS通知（DONE/FAILED）
- [x] 通知タブ（最小）:
  - [x] キュー件数/最終エラー
  - [x] 要確認（needsReview）一覧への入口
  - [x] 最近の解析結果（簡易履歴）
- [ ] 通知履歴の永続化（要件上は「見逃し対策」強化、Phase5候補）

---

## Phase 3: 分析価値の実装（一覧/詳細/集計の本番化）

> 目標: **レシート＋明細行（アイテム）主**で、一覧/詳細/分析を要件レベルへ上げる

### 3.1 一覧/詳細UIを要件へ寄せる
- [x] 一覧: 日時/店名/合計/必須vs裁量バッジ
- [x] 詳細: 明細行表示（元順序、調整行はUIに出さない）
- [x] needsReview時のみ状態表示、欠落や低信頼の強調

### 3.2 必須vs裁量（集計ロジック）
- [x] necessityScoreの金額加重平均（調整行除外）
- [x] 分析タブ: 必須vs裁量比率、無駄遣い候補TOP、月切替

### 3.3 低信頼（要確認）修正フロー
- [x] 修正画面（必須項目 + 支払手段 + 必須度スコア）
- [x] 必須欠落の定義固定（要件準拠）
- [x] 修正完了で needsReview解除 → 集計対象へ

---

## Phase 4: Driveバックアップ（要件どおり）
- [ ] Google Sign-In + appDataFolder
- [ ] JSON export/import（画像除外）
- [ ] 月初処理（archive/full/current-month）
- [ ] 復元/マージ（updatedAt優先）

---

## Phase 5: ブラッシュアップ（品質/UX/モデル調整）

### 5.1 Geminiの応答内容調整
- [ ] プロンプト/スキーマの継続改善（誤分類/割引行/必須欠落/表記ゆれ）
- [ ] 実レシート回帰テスト素材の蓄積（JSON/画像/期待結果）
- [ ] （必要なら）Gemini生レスポンス全体の保存（調査強化）

### 5.2 UIの改善
- [ ] カメラタブ切替時の残像解消
- [ ] 解析中/完了/失敗/要確認の表示を整理（一覧/詳細/通知）
- [ ] Gemini JSON表示の改善（整形/折り畳み/コピー）

---

## 将来
- [ ] オフライン対応（撮影→キュー保持→後で解析）
- [ ] 送信前プレビューの回転/トリミング
- [ ] 検索（店名/商品名）
- [ ] 再解析（モデル切替含む）
- [ ] バックアップ暗号化

