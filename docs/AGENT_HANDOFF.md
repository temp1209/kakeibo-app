# エージェント引き継ぎメモ

**最終更新**: 2026-07-27
**作業ブランチ**: `feat/offline-support`（`main` 取り込み済み、マージ待ち）
**ベース**: `main`（Phase 10 PR #7 / Phase 11 マージ済み、UI/UX ブラッシュアップ・オフライン対応・検索機能 実施済み）

---

## いま何をしているか

**Phase 10・11 はいずれも `main` にマージ済み。UI/UX ブラッシュアップ → オフライン対応・検索機能まで実施。**

| 優先 | Phase | 内容 | 状態 |
|------|-------|------|------|
| ✅ | 9.5 | ブレスト + 要件定義 | PR #6 マージ済 |
| ✅ | **10** | 複数 API・フェイルオーバー | PR #7 マージ済み |
| ✅ | **11** | 予算・通知・分析グラフ・失敗 UI | **完了（`main` マージ）** |
| ✅ | — | UI/UX ブラッシュアップ（使わない設定の整理） | PR #9 マージ済み（下記） |
| ✅ | — | 検索機能（店名/商品名） | PR #10 マージ済み（下記） |
| 仕上げ | — | オフライン対応 | `feat/offline-support` 実装・検証済み、`main` マージ待ち（下記） |
| — | 5.1 | プロンプトチューニング | 実利用並行 |

**次**: `feat/offline-support` を `main` へマージ → 5.1、または残りの将来項目（再解析・通知細分化等）の要否判断

### オフライン対応（2026-07-27・実装・main マージ待ち）

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

1. `main` を最新化して作業ブランチを切る（`feat/offline-support` が未マージなら先にマージ）
2. 後続候補: 5.1 プロンプトチューニング、または将来項目の要否判断（再解析・通知細分化等、`IMPLEMENTATION_PLAN.md`「将来項目」参照）
3. Phase 11 詳細: [`phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md)

CodeGraph: `codegraph_explore` を先に。`projectPath` にリポジトリルート。
