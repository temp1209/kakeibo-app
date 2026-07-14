# 実装計画

> **現行版**（2026-07-11 更新）。旧版は [`archive/`](archive/) を参照。索引は [`README.md`](README.md)。

Phase 7 完了・M2 中核（5.2 解析状態・2.5 通知履歴）完了・**Phase 8 品質ブラッシュアップ完了**（2026-07-12）。

**前版**: [`archive/IMPLEMENTATION_PLAN_REVISED_2026-06-16.md`](archive/IMPLEMENTATION_PLAN_REVISED_2026-06-16.md)（Phase 7 着手時の計画）

---

## 現状の到達点（2026-07-12）

### 完了済み

| Phase | 内容 | 備考 |
|-------|------|------|
| 1〜4 | コア機能（撮影・解析・一覧・分析） | `main` |
| 5（一部） | カメラ/一覧 UI、プロンプト初回調整 | `feat/phase5` マージ済み |
| **6（全項目）** | 1か月フィードバック対応 | PR #2 マージ済み |
| **7（全項目）** | 7.3 / 7.2' / 7.1 | PR #3 マージ済み |
| **5.2** | 解析状態の可視化統一 | 実機確認済み |
| **2.5** | 通知履歴の永続化 | 実機確認済み |
| **8.1〜8.3** | 品質ブラッシュアップ中核 | 実機確認済み |

### Phase 7 — **完了**（PR #3 マージ済み）

オンボーディングの UI ブラッシュアップ・§13 M1/M2 は **Phase 8** で完了（[`plans/phase-8-polish.md`](plans/phase-8-polish.md)）。

### Phase 8 — **完了**（2026-07-12）

| サブ | 内容 | 状態 |
|------|------|------|
| 8.1 | Gemini JSON 閲覧改善 | ✅ |
| 8.2 | オンボーディング UI 磨き込み | ✅ |
| 8.3 | オンボーディング §13 M1/M2 | ✅ |
| 8.4 | §13 低優先（L1/L7 のみ） | 一部 |

詳細: [`plans/phase-8-polish.md`](plans/phase-8-polish.md) / 要件: `REQUIREMENTS.md` §15

### 次のマイルストーン — **Phase 9.5〜11**（2026-07-14 計画）

| Phase | 内容 | 状態 |
|-------|------|------|
| **9.5** | ブレスト + 要件定義 | ✅ ドキュメント（PR #6） |
| **10** | 複数 AI / API・フェイルオーバー | ✅ 実装（実機確認待ち） |
| **11** | 予算・通知・分析 UI・失敗 UX | ⬜ **次に実装** |

詳細: [`plans/phase-9.5-brainstorm.md`](plans/phase-9.5-brainstorm.md) / [`phase-10-multi-ai-provider.md`](plans/phase-10-multi-ai-provider.md) / [`phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md)

**推奨実装順**: Phase 10 → Phase 11

### 並行トラック

| トラック | 内容 |
|----------|------|
| **5.1** | プロンプト・回帰素材（実利用ドリブン） |
| **9.x チューニング** | `NecessityPresetTemplates` の境界ケース数値調整 |
| **8.4 残** | L4/L6 等（任意） |

### Phase 7.2' 手動バックアップ（Drive 代替）

| 項目 | 状態 | 関連 |
|------|------|------|
| Drive 連携の削除 | ✅ | `data/drive/*` 削除、`play-services-auth` 除去 |
| SAF エクスポート/インポート | ✅ | `FileBackupOrchestrator`, `FileBackupLaunchers` |
| 設定 UI | ✅ | `SettingsScreen.kt` |
| 月次リマインド | ✅ 実機確認 | 一覧タブで表示、エクスポート後は再表示なし |
| エクスポート・復元 | ✅ 実機確認 | SAF 保存、削除 → インポート確認済み |

詳細: [`plans/backup-manual-migration.md`](plans/backup-manual-migration.md)

### 未完了（本計画で再配置）— 上記「次のマイルストーン」へ移行済み

~~Phase 5 残、2.5、7.1 後回し~~ → Phase 8 / 5.1 継続に再配置（2026-07-11）

---

## 07-11 までの主な判断

| 判断 | 理由 |
|------|------|
| **Drive 自動バックアップを廃止** | 個人利用では OAuth / 403・404 / manifest 運用コストがメリットを上回る。開発停滞の主因だった |
| **手動 JSON + 月次リマインド** | `data/backup/` のエクスポート/マージ資産を活かしつつ、忘れ防止の UX を最小コストで追加 |
| **git 履歴は保持** | Drive v1/v2 の試行はコミット `9802557` 等に残る。ポートフォリオでは「スコープ整理」として説明可能 |
| **7.2（Drive 403 修正）はクローズ** | 方針変更により対象外。旧タスクは本計画では **7.2'** として置き換え |

---

## 全体方針（2026-07-12）

1. **コア体験は完成** — 撮影 → 解析 → 一覧/分析 + 手動バックアップ + 初回セットアップ。
2. **M2 / Phase 8 は完了** — 解析状態・通知履歴・品質ブラッシュアップ。
3. **次は実利用** — 5.1 プロンプトを困りごとに応じて継続。バグのみ小 PR。
4. **スコープ拡大は控えめ** — 8.4 残・将来機能は必要時のみ。

---

## Phase 7: 実利用で顕在化したギャップ

> 目標: **初回体験の穴埋めと、実用的なデータ保全**  
> 詳細メモ: [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md)（要更新）

### 7.3 API キー未設定時の送信フィードバック — ✅ 完了

**完了条件（達成済み）**: APIキー未設定で送信確定しようとすると理由が表示され、設定へ誘導される。

**関連コード**: `MainActivity.kt`, `AnalysisWorker.kt`, `GeminiApiKeyStore.kt`, `NotificationsScreen.kt`, `ReceiptDetailScreen.kt`

---

### 7.2' 手動 JSON バックアップ — ✅ 実装済み（実機確認一部済み）

**背景**: 06-16 計画の 7.2（Drive 403）を経て、07-11 に Drive 連携全体を廃止し、個人利用向けの手動保全に集約。

- [x] **7.2'.1 Drive 連携の削除**
  - `data/drive/*`, `DriveBackupWorker`, `DriveBackupScheduler`, `DriveBackupPrefs`
  - `play-services-auth`, `datastore-preferences` 依存除去
- [x] **7.2'.2 手動エクスポート/インポート**
  - `FileBackupOrchestrator` — `FULL_SNAPSHOT` 単一 JSON
  - SAF `CreateDocument` / `OpenDocument`
  - `BackupMerge` による復元（`updatedAt` 優先）
- [x] **7.2'.3 安全ガード（簡易）**
  - ローカル 0 件でのエクスポート拒否
  - インポート前の確認ダイアログ（件数・日時表示）
- [x] **7.2'.4 月次リマインド**
  - 一覧タブ表示時、今月未エクスポートならダイアログ（最大月1回）
  - 「今すぐバックアップ」で SAF エクスポートへ直行
- [x] **7.2'.5 実機スモーク** — 2026-07-11 実機確認
  - [x] 一覧タブ遷移時に月次ダイアログ表示
  - [x] 「今すぐバックアップ」で JSON 保存、ファイル中身確認
  - [x] エクスポート後、再度一覧タブへ遷移してもダイアログ非表示
  - [x] 削除 → インポート → 件数確認
  - ~~「今月は表示しない」~~ — UI 廃止（「あとで」+「今すぐバックアップ」のみ）
- [x] **7.2'.6 ドキュメント同期**
  - `REQUIREMENTS.md` §12/§13、`EXTERNAL_SETUP.md`、`DEBUGGING_GUIDE.md`、本ファイル

**完了条件**: 設定から JSON の保存・復元が実機で安定し、README / 要件と一致している。

**関連コード**: `FileBackupOrchestrator.kt`, `FileBackupLaunchers.kt`, `SettingsScreen.kt`, `MonthlyBackupPromptDialog.kt`

**設計メモ**: [`plans/backup-manual-migration.md`](plans/backup-manual-migration.md)

---

### 7.2 Drive バックアップ 403 修正 — 🚫 クローズ（スコープ外）

06-16 計画の 7.2 は **Drive 連携廃止により不要**。調査知見（SHA-1、403 切り分け）は `docs/daily/2026-06-16.md` と git 履歴に残す。

---

### 7.1 初回実行オンボーディング — ✅ 完了（実機確認済み・2026-07-11）

> **詳細計画**: [`plans/onboarding.md`](plans/onboarding.md)

**背景**: チュートリアル・APIキー・権限要求が要件断片のみ。7.3 は送信時ガードだが、「設定タブの存在を知らない」問題は残る。Drive 説明が不要になった分、ウィザードは軽量化できる。

- [x] **7.1.1 初回フラグとウィザード（最小）**
  - SharedPreferences 等で `onboardingCompleted` を保持
  - 1回限り: 歓迎 → APIキー入力（設定と同 UI、スキップ可だが警告）→ カメラ許可 → 通知許可（API 33+）
- [x] **7.1.2 APIキー未設定時の常時ガード**
  - カメラタブに未設定バナー or 設定ショートカット（7.3 と共通化）
- [x] **7.1.3 バックアップの軽い案内（任意）**
  - 完了画面に「設定から JSON でバックアップ」の1行テキスト
- [x] **7.1.4 要件・README との整合**
  - `REQUIREMENTS.md` §14 にオンボーディングフローを追記

**完了条件**: 新規インストール後、設定タブを知らなくても最低限のセットアップに誘導される。

**後回し（ブラッシュアップ）**: ウィザードの見た目・遷移の磨き込みは Phase 5.2 等の後。詳細は [`plans/onboarding.md`](plans/onboarding.md) §12。

---

## Phase 5 残タスク（Phase 7 後半〜並行可）

### 5.2 UI の改善（残）

- [x] **解析状態の可視化統一** — 詳細計画: [`plans/phase-5-2-analysis-status.md`](plans/phase-5-2-analysis-status.md) ✅

- [x] **Gemini JSON 閲覧改善** — Phase 8.1 ✅（[`plans/phase-8-polish.md`](plans/phase-8-polish.md)）

### 5.1 Gemini 継続チューニング（残）

- [x] 回帰テスト素材の最小セット（`docs/fixtures/necessity_score_regression.json`）
- [ ] 誤分類・割引行・表記ゆれのプロンプト差分
- [ ] 実レシート画像付き回帰素材の蓄積
- [ ] necessityScore 改訂後の主観確認
- [ ] （必要なら）生レスポンス全体の保存

### 2.5 通知履歴の永続化 — ✅ 完了（実機確認済み）

- [x] Room `analysis_notification_events` テーブル（最大100件保持）
- [x] `AnalysisWorker` で解析完了 / 要確認 / 失敗時に記録
- [x] 通知タブ「通知履歴」セクションで表示

---

## Phase 8: 品質ブラッシュアップ — ✅ 完了（2026-07-12）

> **詳細計画**: [`plans/phase-8-polish.md`](plans/phase-8-polish.md)  
> **要件**: `REQUIREMENTS.md` §15

| サブ | 内容 | 状態 |
|------|------|------|
| 8.1 | Gemini JSON（pretty-print・コピー・BottomSheet） | ✅ |
| 8.3 | オンボーディング M1/M2（権限再評価・二重遷移） | ✅ |
| 8.2 | オンボーディング UI（ステップ表示・文言） | ✅ |
| 8.4 | §13 低優先（L1/L7 のみ） | 一部 |

**完了条件**: 達成済み（実機確認 2026-07-12）。

---

## Phase 9: 必須度ポリシー — 実装完了（実機・PR 待ち）

> **詳細計画**: [`plans/phase-9-necessity-policy.md`](plans/phase-9-necessity-policy.md)  
> **要件**: `REQUIREMENTS.md` §16  
> **ブランチ**: `feat/phase9-necessity-policy`

| サブ | 内容 | 状態 |
|------|------|------|
| 9.1 | データモデル・プリセットテンプレ | ✅ |
| 9.2 | 方針コンパイル AI + 設定 UI | ✅ |
| 9.3 | 解析プロンプトへの注入 | ✅ |
| 9.4 | 訂正例登録・未反映バナー | ✅ |
| 9.5 | 当月再スコア Worker | ✅ |
| 9.6 | バックアップ 1.2 | ✅ |
| 9.7 | 実機・回帰 | ✅ |
| 9.8 | プロンプト `data/prompt/` 切り出し・プリセット別境界ケース | ✅ |

**完了条件**: 達成済み（実機確認 2026-07-12・`main` マージ済み）。

---

## Phase 9.5: ブレスト・要件定義 — ✅ ドキュメント

> [`plans/phase-9.5-brainstorm.md`](plans/phase-9.5-brainstorm.md)

---

## Phase 10: 複数 AI / API プロバイダ ← **次に着手**

> [`plans/phase-10-multi-ai-provider.md`](plans/phase-10-multi-ai-provider.md)

---

## Phase 11: 予算・通知・分析 UI

> [`plans/phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md)

---

## 推奨実装順序（2026-07-12〜）

| 順 | 項目 | Phase | 状態 |
|----|------|-------|------|
| — | M2 中核 + Phase 8 | 5.2 / 2.5 / 8.x | ✅ 完了 |
| **1** | **複数 AI / API** | 10 | **次** |
| **2** | **予算・通知・分析 UI** | 11 | 未着手 |
| — | プロンプト継続チューニング | 5.1 | 実利用並行 |
| 3 | §13 低優先（L4/L6 等） | 8.4 | 任意 |
| — | ポートフォリオ整備 | M4 | 任意 |

---

## マイルストーン案

### M0 — 手動バックアップの本線取り込み — ✅ 完了

- 7.2' マージ済み（PR #3）
- 実機スモーク: エクスポート・月次リマインド・復元 ✅
- ドキュメント同期 ✅（7.2'.6）

**出口**: ✅ データ保全が「設定から JSON」で説明・運用できる。

---

### M1 — 初回体験（2〜3 セッション）

- ~~7.1 オンボーディング~~ ✅ 完了（ブラッシュアップ・§13 改善は後回し）

**出口**: ✅ 新規インストールから迷わず撮影・解析まで到達できる。

---

## Phase 7 マージ（2026-07-11）

PR [#3](https://github.com/temp1209/kakeibo-app/pull/3) で `main` にマージ済み。

---

### M2 — 品質・運用の仕上げ — ✅ 中核完了

- [x] 5.2 解析状態可視化
- [x] 2.5 通知履歴永続化（実機確認済み）
- ~~5.2 Gemini JSON~~ → Phase 8.1 へ移管
- 5.1 プロンプト継続 → 並行トラック

**出口（中核）**: ✅ 解析状態が一覧/詳細/通知で統一。通知見逃し対策の履歴あり。

---

### M2.5 — Phase 8 品質ブラッシュアップ — ✅ 完了

- [x] 8.1 Gemini JSON
- [x] 8.3 オンボーディング技術改善
- [x] 8.2 オンボーディング UI
- [x] 8.4 一部（L1/L7）

**出口**: 調査・初回体験の「粗さ」が解消。コアフローの回帰なし（実機確認 2026-07-12）。

---

### M3 — Phase 9 必須度ポリシー — ✅ 完了

- [x] 方針コンパイル（ハイブリッド）+ 設定 UI
- [x] 解析・当月再スコアへの反映
- [x] バックアップにユーザー設定を含める
- [x] プロンプト `data/prompt/` 切り出し・プリセット別境界ケース
- [x] 実機スモーク（プリセット変更 → 解析 → 再スコア）

詳細: [`plans/phase-9-necessity-policy.md`](plans/phase-9-necessity-policy.md)

---

### M4 — Phase 10 / 11 ← **現在地**

- [x] Phase 9.5 ブレスト + 要件ドキュメント
- [ ] Phase 10 複数 AI / API・フェイルオーバー
- [ ] Phase 11 予算・通知・分析 UI

詳細: [`plans/phase-9.5-brainstorm.md`](plans/phase-9.5-brainstorm.md)

---

### M5 — ポートフォリオ整備（任意）

- README のデモ手順・スクリーンショット更新
- コア体験の実機デモ動画（撮影 → 解析 → 分析）
- `docs/daily/` に 07-11 日報（スコープ判断の記録）

---

## 将来項目

- [ ] **設定タブ APIキー UI**: 保存済み時は「変更する」で入力欄展開（常時表示しない）— [`REQUIREMENTS.md`](REQUIREMENTS.md) §13
- [ ] オフライン対応（撮影 → キュー保持 → 後解析）
- [ ] 送信前プレビューの回転/トリミング
- [ ] 検索（店名/商品名）
- [ ] バックアップ暗号化（パスフレーズ）
- [ ] Android Auto Backup で API キーを除外（`backup_rules.xml`）
- [ ] ~~Drive 自動バックアップ~~ — 個人利用スコープでは再検討しない（履歴は git に残存）

---

## ブランチ運用（07-11 時点）

| ブランチ / 状態 | 内容 | 次のアクション |
|-----------------|------|----------------|
| `main` | Phase 6 + Phase 7 完了 | 通常開発のベース |
| `origin/main` | リモートと同期 | `git pull` |

---

## 要件との差分メモ

| 要件（当初） | 現方針 | 備考 |
|--------------|--------|------|
| Drive 日次バックアップ | **廃止** → 手動 JSON + 月次リマインド | ✅ 7.2' 完了 |
| APIキー「初回起動で入力」 | **7.1 オンボーディング + 7.3 ガード** | ✅ 完了 |
| 解析失敗時の自動リトライ | **手動再送信のみ** | Phase 6.2 で実装済み |
| 低信頼修正は編集範囲を絞る | **手入力同等 UI** | Phase 6.3 で実装済み |
| 手動バックアップボタン | **実装済み**（設定 + 月次ダイアログ） | 当初は「将来検討」だった |

---

## 参照

- [`archive/IMPLEMENTATION_PLAN_REVISED_2026-06-16.md`](archive/IMPLEMENTATION_PLAN_REVISED_2026-06-16.md) — 前版（Phase 7 着手時）
- [`archive/IMPLEMENTATION_PLAN_REVISED_2026-06-12.md`](archive/IMPLEMENTATION_PLAN_REVISED_2026-06-12.md) — Phase 6 定義
- [`plans/backup-manual-migration.md`](plans/backup-manual-migration.md) — Drive 廃止・手動バックアップ設計
- [`plans/phase-9-necessity-policy.md`](plans/phase-9-necessity-policy.md) — **Phase 9 必須度ポリシー（完了）**
- [`plans/phase-5-2-analysis-status.md`](plans/phase-5-2-analysis-status.md) — Phase 5.2 解析状態可視化（完了）
- [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) — バックログ（§4 に 7.1 精査後回し項目）
- [`REQUIREMENTS.md`](REQUIREMENTS.md) — 要件定義
- [`daily/2026-06-16.md`](daily/2026-06-16.md) — Phase 7.2/7.3 + Drive v2 試行の日報
- [`archive/feedback/1か月使った感想.txt`](archive/feedback/1か月使った感想.txt) — Phase 6 の元フィードバック
