# エージェント引き継ぎメモ

**最終更新**: 2026-08-16
**作業ブランチ**: なし（`main` が最新。新規開発は一旦停止中）
**ベース**: `main`（Phase 7〜11・UI/UXブラッシュアップ・検索・オフライン対応・レシート日付誤読修正まで全てマージ済み）

---

## いま何をしているか

**2026-08-16、レシート日付誤読修正のブランチ（`claude/nakau-receipt-date-recognition-adow19`）をマージし、`main` は開発上の未マージ作業が無い状態になった。**

このタイミングで実利用フィードバックとして「レシート送信後、解析が返ってこないことが多い（体感半分ほど）。解析キューに約10件滞留」という新しい不具合が発覚した（[`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) §5）。コードレビューでは原因を「解析処理自体の失敗」ではなく「`WorkManager` のジョブ投入・実行がバッテリー最適化等で滞っている」可能性が高いと推測したが、実機ログでの裏付けはまだ無い。

**方針**: 新規機能開発は一旦停止し、以下を優先する。

1. 端末側の設定（バッテリー最適化の対象外設定など）を見直す
2. しばらく実利用しながら、解析キューの滞留が解消するか・再発するかを観察する
3. 再発するようなら Logcat（フィルタ: `AnalysisWorker|AiRequestRouter`）や `analysis_queue` テーブルの中身を確認し、原因を特定する

次にエージェントとして着手する際は、まず [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) §5 の状況（ユーザーが様子見した結果どうだったか）を確認すること。

| 優先 | 内容 | 状態 |
|------|------|------|
| ✅ | Phase 1〜11・UI/UXブラッシュアップ・検索・オフライン対応 | 全て `main` マージ済み |
| ✅ | レシート日付誤読修正・Geminiモデル更新(gemini-3.6-flash)・配布CI・PIIガードレール | PR #14 マージ済み（2026-08-16） |
| ✅ | Firebase App Distribution 外部セットアップ | 完了（2026-08-16、下記） |
| 🔍 | 解析キューの滞留（体感50%返ってこない） | 調査中・様子見。`KNOWN_ISSUES.md` §5 |
| — | 5.1 プロンプトチューニング等の将来項目 | 保留（滞留問題が優先） |

### レシート日付誤読修正・関連対応（2026-08-16・実施済み・PR #14）

- AI誤読(年ズレ)修正、日付表記の汎用対応、端末保存時刻との突き合わせによる機械的な異常検知の保険（`GeminiStrictParser.detectDateAnomaly`）
- Geminiモデル名を `gemini-2.5-flash` → `gemini-3.6-flash` に更新（旧モデルが新規APIキーで404を返すようになったため。3.6はGA・画像入力対応をWeb検索で確認済み）
- push時にFirebase App Distributionへ自動配布するCI（`.github/workflows/distribute.yml`）を追加。GitHub Secrets（`FIREBASE_APP_ID` / `FIREBASE_SERVICE_ACCOUNT_JSON`）の登録と外部セットアップは2026-08-16に完了、動作確認済み（下記「Firebase App Distribution 外部セットアップ」）
- 個人情報のコミットを防ぐガードレール: `CLAUDE.md` にルールを明文化、`scripts/check-no-pii.sh`、CI（`.github/workflows/pii-check.yml`）
- 関連コード: `GeminiStrictParser.kt`, `GeminiAiProvider.kt`, `GeminiClient.kt`, `ReceiptAnalysisPrompt.kt`

### Firebase App Distribution 外部セットアップ（2026-08-16・完了）

`.github/workflows/distribute.yml`（PR #14でmainにマージ済み）を実際に動かすための外部設定が完了し、テストpushで `distribute` ジョブの成功を確認済み。

- Firebaseプロジェクト作成・Androidアプリ登録（パッケージ名 `work.temp1209.kakeibo`）・App Distribution有効化・テスターグループ「self」作成
- サービスアカウントに「Firebaseアプリ配布管理者」（`roles/firebaseappdistro.admin`）ロールを付与しJSONキーを発行
- GitHub Secrets（`FIREBASE_APP_ID` / `FIREBASE_SERVICE_ACCOUNT_JSON`）に登録
- **ハマった点**: kakeibo-app用のFirebase/GCPプロジェクトが2つ存在していた（`kakeibo-app-dev`＝過去のDrive連携等で作成された古い未使用プロジェクト、と現行プロジェクト）。サービスアカウントを誤って古い方に作成してしまい、`distribute` ジョブが403エラーで失敗。現行プロジェクト側で作り直して解決。**今後Firebase/GCP Consoleを操作する際は、必ず現行プロジェクトが選択されていることを確認すること**（`kakeibo-app-dev` には触らない）
- 未確認: Pixel 8aでの実機受信（Firebase App Testerアプリでの通知・インストール確認）はユーザー側の作業として残っている可能性あり
- 詳細手順: `docs/EXTERNAL_SETUP.md` §5

### 解析キューの滞留（2026-08-16・調査中）

詳細は [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) §5 を参照。要点のみ:

- `AnalysisWorker` 自体はAPI失敗を必ず `FAILED` に確定させる作りなので、「エラー通知も無く静かに滞留する」のは処理失敗ではなく `WorkManager` のジョブ実行が滞っている可能性が高い
- `scheduleAnalysisWork()` の `enqueueUniqueWork(..., ExistingWorkPolicy.KEEP, ...)` が、前回ジョブが残っている間は新規投入をスキップする点が怪しい
- バッテリー最適化・Doze モードの影響を確認する必要あり（未実施）
- `failStaleQueueEntries()`（7日超で強制失敗）は既にあるが、今回のような短期滞留には効かない

### オフライン対応（2026-07-27・実装・PR #11 マージ済み）

調査の結果、撮影・キュー投入・`WorkManager`（`NetworkType.CONNECTED`制約）による自動待機・復帰後実行は既に成立済みだった（大きな新機能ではなく穴埋め2点）。

- **画像40日retentionの穴埋め**: `cleanupExpiredImages()` が `analysisStatus` を見ておらず、長期オフラインで解析未完了のまま40日を過ぎると元画像が消える不具合を修正（`isImageCleanupEligible()`）
- **長期未処理キューの異常検出**: 実利用では長期の通信断はまず発生しないため、無期限のオフライン許容はしない方針に転換。投入から**7日**超のQUEUED/RUNNINGは「バグでスタック」とみなし解析失敗に確定（`failStaleQueueEntries()` / `isQueueEntryStale()`）。アプリ起動時に実行
- 関連コード: `ReceiptRepository.kt`（`isImageCleanupEligible`, `isQueueEntryStale`, `failStaleQueueEntries`）, `ReceiptDao.kt`, `MainActivity.kt`
- 詳細: `IMPLEMENTATION_PLAN.md`「07-27 の主な判断」、`REQUIREMENTS.md` §1

### 検索機能（2026-07-27・実施済み・PR #10）

- 一覧タブ右上の検索アイコンでトグル開閉（常時表示ではない）。店名・商品名の部分一致、全期間対象
- 検索欄の表示状態・検索語はレシート詳細画面への遷移・復帰をまたいで保持（`MainActivity` にホイスト）
- 表示中に再度アイコンを押すと、窓を閉じて検索語もクリア
- 関連コード: `ReceiptsListScreen.kt`, `ReceiptRepository.searchReceiptRows`, `ReceiptDao.searchReceiptRows`

### UI/UX ブラッシュアップ（2026-07-27・実施済み・PR #9）

1か月以上の実利用フィードバックに基づき、触ったことのない・冗長な設定を削減。

- **通知設定を3項目に統合**: 「すべての通知」マスター + 「解析が失敗・要確認のとき」（失敗系）+ 「解析が完了したとき」（成功系）+ 「予算通知」（定期確認・80%・100%をまとめて1トグル）。旧・項目別トグルの値は初回アクセス時に自動移行（`NotificationPrefs.migrateLegacyIfNeeded`）
- **予算の集計対象を削除**: 「すべての支出」のみに固定。`BudgetAggregateMode` はデータモデルごと削除（バックアップ schema からも `aggregateMode` を除去、実利用で未使用だったため）
- **APIキー追加時に自動疎通確認**: 追加ボタン押下時に裏で `router.testSlot()` を実行し、結果をスナックバーに表示。手動の「疎通」ボタンは削除
- 関連コード: `NotificationPrefs.kt`, `NotificationSettingsSection.kt`, `BudgetStore.kt`, `BudgetProgress.kt`, `AiProviderSlotsSection.kt`

### Phase 11 要約（as-built）

- 月次予算（オンボーディング / 設定 / 分析積み上げ棒）
- 予算進捗通知（10/20/月末・80%/100%、同月内キャッチアップ）
- 解析失敗理由の一覧表示、バックアップ schema 1.3（`budget`）
- `NO_RECEIPT` / `[NO_RECEIPT]` → 解析失敗
- 通知・予算集計対象の設定簡略化は上記「UI/UX ブラッシュアップ」で実施済み

---

## Phase 10 要約（as-built）

- **Gemini 複数スロット**（初版。他プロバイダ種は未追加）
- 例外なら常に次スロットへ。パース失敗はルータ外
- 設定: 追加ダイアログ（追加時に自動疎通確認） / 削除 / ↑↓・長押しドラッグ。キーの細かい編集 UI は無し
- 詳細: [`plans/phase-10-multi-ai-provider.md`](plans/phase-10-multi-ai-provider.md)

### 実機確認チェックリスト

- [x] 既存単一キーが「メイン」スロット 1 件に見える
- [x] ダミーを 1 番・本番を 2 番 → Logcat **`AiRequestRouter`**: `route start order=...` → `failover` → `success attempt=2/2`
- [x] 本番だけ 1 番 → 切替なしで成功（`attempt=1/1`）
- [x] 両方無効 → 全滅メッセージ
- [x] 方針コンパイル・必須度再スコアも同様に動作
- [x] バックアップ JSON に API キーが含まれない
- [x] オンボーディングのキー保存が「メイン」を壊さない（複数スロット時）

実機確認日: 2026-07-18。特に問題なし。

### Logcat

フィルター例: `AiRequestRouter|AnalysisWorker|AiProviderStore`
（`Analytics` では出ない）

---

## ドキュメント

| Phase | パス |
|-------|------|
| 9.5 ブレスト | [`plans/phase-9.5-brainstorm.md`](plans/phase-9.5-brainstorm.md) |
| 10 複数 API | [`plans/phase-10-multi-ai-provider.md`](plans/phase-10-multi-ai-provider.md) |
| 11 予算・通知 | [`plans/phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md) |

---

## 推奨セッション開始手順

1. `main` を最新化（未マージブランチは無いはずだが、`git branch -a` で確認）
2. [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) §5（解析キューの滞留）の状況をユーザーに確認 — 解消していれば §5 をクローズ、再発していれば実機ログでの原因調査に着手
3. 上記が片付いていれば、5.1 プロンプトチューニング、または将来項目の要否判断（`IMPLEMENTATION_PLAN.md`「将来項目」参照）

CodeGraph: `codegraph_explore` を先に。`projectPath` にリポジトリルート。
