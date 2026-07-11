# 実装計画

> **現行版**（2026-07-11）。旧版は [`archive/`](archive/) を参照。索引は [`README.md`](README.md)。

このファイルは、**Phase 6 の `main` マージ完了**、**Phase 7.3（APIキーガード）の実装**、および **Drive バックアップ廃止 → 手動 JSON バックアップへの移行**（2026-07-11）を踏まえ、次に着手すべき項目を優先度付きで整理し直したもの。

**前版**: [`archive/IMPLEMENTATION_PLAN_REVISED_2026-06-16.md`](archive/IMPLEMENTATION_PLAN_REVISED_2026-06-16.md)（Phase 7 着手時の計画）

---

## 現状の到達点（2026-07-11）

### 完了済み

| Phase | 内容 | 備考 |
|-------|------|------|
| 1〜4 | コア機能（撮影・解析・一覧・分析） | `main` |
| 5（一部） | カメラ/一覧 UI、プロンプト初回調整 | `feat/phase5` マージ済み |
| **6（全項目）** | 1か月フィードバック対応 | PR #2 マージ済み（`a1b0038`） |
| **7.3** | APIキー未設定時の送信ガード | `9802557` |
| 7.2' | 手動 JSON バックアップ + 月次リマインド | PR #3 マージ済み、実機確認済み |
| **7.1** | 初回オンボーディング | PR #3 マージ済み、実機確認済み |
| **7.2'.6** | ドキュメント同期（Drive 記述整理） | 2026-07-11 本更新 |

### Phase 7 — **完了**（PR #3 マージ済み・2026-07-11）

Phase 7 の実装タスクは一通り完了。オンボーディングの UI ブラッシュアップ・コード精査の改善項目は後回し（[`plans/onboarding.md`](plans/onboarding.md) §12–13、[`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) §4）。

### Phase 7.2' 手動バックアップ（Drive 代替）

| 項目 | 状態 | 関連 |
|------|------|------|
| Drive 連携の削除 | ✅ | `data/drive/*` 削除、`play-services-auth` 除去 |
| SAF エクスポート/インポート | ✅ | `FileBackupOrchestrator`, `FileBackupLaunchers` |
| 設定 UI | ✅ | `SettingsScreen.kt` |
| 月次リマインド | ✅ 実機確認 | 一覧タブで表示、エクスポート後は再表示なし |
| エクスポート・復元 | ✅ 実機確認 | SAF 保存、削除 → インポート確認済み |

詳細: [`plans/backup-manual-migration.md`](plans/backup-manual-migration.md)

### 未完了（本計画で再配置）

| 由来 | 項目 |
|------|------|
| Phase 5 残 | 解析状態可視化、Gemini JSON 改善、プロンプト継続チューニング |
| Phase 2.5 残 | 通知履歴の永続化 |
| 7.1 後回し | UI ブラッシュアップ、コード精査改善（§13） |

---

## 07-11 までの主な判断

| 判断 | 理由 |
|------|------|
| **Drive 自動バックアップを廃止** | 個人利用では OAuth / 403・404 / manifest 運用コストがメリットを上回る。開発停滞の主因だった |
| **手動 JSON + 月次リマインド** | `data/backup/` のエクスポート/マージ資産を活かしつつ、忘れ防止の UX を最小コストで追加 |
| **git 履歴は保持** | Drive v1/v2 の試行はコミット `9802557` 等に残る。ポートフォリオでは「スコープ整理」として説明可能 |
| **7.2（Drive 403 修正）はクローズ** | 方針変更により対象外。旧タスクは本計画では **7.2'** として置き換え |

---

## 全体方針（2026-07-11 時点）

1. **コア体験を主役に戻す** — 撮影 → 解析 → 一覧/分析。保全は「動く手動バックアップ」で十分。
2. **Phase 7 は完了** — 7.3 / 7.2' / 7.1 は PR #3 で `main` マージ済み。
3. **次は Phase 2.5** — 通知履歴の永続化。
4. **ドキュメントは現状と同期済み** — 要件・外部設定・デバッグ手順から Drive 前提を除去または archive へ。

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

- [ ] **Gemini JSON 閲覧改善**
  - 整形表示、折りたたみ、クリップボードコピー

### 5.1 Gemini 継続チューニング（残）

- [x] 回帰テスト素材の最小セット（`docs/fixtures/necessity_score_regression.json`）
- [ ] 誤分類・割引行・表記ゆれのプロンプト差分
- [ ] 実レシート画像付き回帰素材の蓄積
- [ ] necessityScore 改訂後の主観確認
- [ ] （必要なら）生レスポンス全体の保存

### 2.5 通知履歴の永続化 — ✅ 実装済み（2026-07-11）

- [x] Room `analysis_notification_events` テーブル（最大100件保持）
- [x] `AnalysisWorker` で解析完了 / 要確認 / 失敗時に記録
- [x] 通知タブ「通知履歴」セクションで表示

---

## 推奨実装順序（2026-07-11 〜）

| 順 | 項目 | Phase | 規模 | 状態 |
|----|------|-------|------|------|
| — | Phase 7（7.3 / 7.2' / 7.1 / ドキュメント同期） | 7 | — | ✅ 完了 |
| **1** | **解析状態可視化** | 5.2 | M | ✅ 完了 |
| **2** | **通知履歴永続化** | 2.5 | M | ✅ 実装済み |
| **3** | **Gemini JSON 改善** | 5.2 | S | **次に着手** |
| 4 | プロンプト継続チューニング | 5.1 | 継続 | 未着手 |
| 5 | オンボーディング UI ブラッシュアップ | 7.1 後回し | S | 任意 |

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

### M2 — 品質・運用の仕上げ（Phase 5 残）← **現在地**

- [x] 5.2 解析状態可視化 — [`plans/phase-5-2-analysis-status.md`](plans/phase-5-2-analysis-status.md) ✅ 実機確認済み
- [x] **2.5 通知履歴永続化** ← 実装済み（実機確認待ち）
- 5.2 Gemini JSON 改善
- 5.1 プロンプト・回帰素材の継続

---

### M3 — ポートフォリオ整備（任意）

- README のデモ手順・スクリーンショット更新
- コア体験の実機デモ動画（撮影 → 解析 → 分析）
- `docs/daily/` に 07-11 日報（スコープ判断の記録）

---

## 将来項目

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
- [`plans/phase-5-2-analysis-status.md`](plans/phase-5-2-analysis-status.md) — Phase 5.2 解析状態可視化（完了）
- [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) — バックログ（§4 に 7.1 精査後回し項目）
- [`REQUIREMENTS.md`](REQUIREMENTS.md) — 要件定義
- [`daily/2026-06-16.md`](daily/2026-06-16.md) — Phase 7.2/7.3 + Drive v2 試行の日報
- [`archive/feedback/1か月使った感想.txt`](archive/feedback/1か月使った感想.txt) — Phase 6 の元フィードバック
