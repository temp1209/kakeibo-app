# 既知の課題・バックログ（実利用フィードバック）

実機利用や開発中に気づいた、**未対応または要件に未明文化**の項目を集約する。  
実装タスクの詳細は [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) を参照。

最終更新: 2026-08-16

---

## 1. 初回実行時オンボーディングが未整備

| 項目 | 内容 |
|------|------|
| **状態** | ✅ 解決済み（2026-07-11、Phase 7.1） |
| **発見** | 2026-06-16 実利用 |

### 症状・ギャップ（解決前）

- 初回起動時に **チュートリアル / セットアップウィザード** がない
- **Gemini API キー** は要件上「初回起動で入力」とあるが、起動直後の誘導はなく **設定タブを自分で開く** 必要がある
- **カメラ権限**・**通知権限（Android 13+）** の説明付きリクエストフローが体系化されていない
- 上記が実装計画のチェックリストとして明文化されていなかった

### 実装

- `OnboardingPrefs` + `OnboardingWizard`（5ステップ: ようこそ → APIキー → カメラ → 通知 → 完了）
- `MainActivity` で初回ゲート。通知 deep link 時は Wizard スキップ
- `GeminiApiKeyInputSection` で設定と入力 UI 共通化
- カメラタブに APIキー未設定バナー（設定タブへ導線）
- 詳細: [`plans/onboarding.md`](plans/onboarding.md)

### 関連

- 要件: `REQUIREMENTS.md` §14（オンボーディング）、§10（APIキー）、§3（通知・カメラ）
- UI: `OnboardingWizard.kt`, `SettingsScreen.kt`, `CameraScreen.kt`

---

## 2. Drive バックアップ: ログイン成功後にパーミッションエラー

| 項目 | 内容 |
|------|------|
| **状態** | 🚫 クローズ（2026-07-11）— Drive 連携廃止（Phase 7.2'） |
| **発見** | 2026-06-16 実利用 |

Drive 自動バックアップは手動 JSON バックアップに置き換え済み。調査知見は git 履歴・`docs/daily/2026-06-16.md` に残す。

---

## 3. API キー未設定時、レシート送信で理由が表示されない

| 項目 | 内容 |
|------|------|
| **状態** | ✅ 解決済み（2026-07-11、Phase 7.3） |
| **発見** | 2026-06-16 実利用 |

### 実装

- プレビュー送信前ガード（`MainActivity.confirmPreviewOrShowApiKeyDialog`）
- Worker 二重防御（キー未設定時 `FAILED` + `MISSING_KEY_USER_MESSAGE`）
- Phase 7.1 でオンボーディング・カメラバナー追加

---

## 4. Phase 7.1 オンボーディング — 技術的改善バックログ（後回し）

| 項目 | 内容 |
|------|------|
| **状態** | バックログ（クリティカルなし・2026-07-11 精査） |
| **詳細** | [`plans/onboarding.md`](plans/onboarding.md) **§13** |

優先度の高いもの（M1/M2）は **Phase 8.3** で着手予定。詳細: [`plans/phase-8-polish.md`](plans/phase-8-polish.md)

---

## 5. 解析キューが滞留し、レシート解析が返ってこないことがある

| 項目 | 内容 |
|------|------|
| **状態** | 🔍 調査中・様子見（2026-08-16 発見） |
| **発見** | 2026-08-16 実利用（レシート送信後、体感半分ほどが「解析待ち」のまま返ってこない。`analysis_queue` に約10件滞留） |

### 症状

- レシートを送信しても解析が完了せず、一覧上で「解析待ち」（PENDING/RUNNING）のまま止まる
- `FAILED`・`NEEDS_REVIEW` の通知が来ているわけではなく、静かに滞留する

### 調査で分かったこと（コードレビューベース、実機ログ未採取）

- `AnalysisWorker.doWork()` はキューを `while` ループで全件処理し、API 呼び出し失敗時は例外を捕捉して確実に `FAILED` にする実装になっている（[`AnalysisWorker.kt`](../app/src/main/java/work/temp1209/kakeibo/data/analysis/AnalysisWorker.kt)）。`GeminiClient` にも `callTimeout(180s)` 等が設定済みで、API呼び出し自体が無限にハングすることは考えにくい
- そのため「エラーにもならず滞留する」原因は、解析処理そのものより **`WorkManager` へのジョブ投入・実行が滞る側**の可能性が高いと推測
  - `scheduleAnalysisWork()` は `enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, ...)` かつ `Constraints(NetworkType.CONNECTED)` 付き（[`ReceiptRepository.kt`](../app/src/main/java/work/temp1209/kakeibo/data/ReceiptRepository.kt)）。`KEEP` のため、既存の未完了ジョブが残っている間は新規ジョブが投入されない
  - Android のバッテリー最適化・Doze モードにより `WorkManager` の実行自体がOS側で遅延・スキップされている可能性がある（特に端末メーカーの独自最適化が強い場合）
  - `feat/offline-support`（PR #11）で「投入から7日超のQUEUED/RUNNINGは異常とみなし解析失敗に確定」という保険（`failStaleQueueEntries()`）は実装済みだが、しきい値が7日のため**直近数時間〜数日の短期滞留には効かない**
- Gemini モデル名は `gemini-2.5-flash` → `gemini-3.6-flash` に更新済み（PR #14、2026-08-16マージ）。3.6-flashはGA・画像入力対応をWeb検索で確認済みのため、モデル名不正による404が原因の可能性は低いと判断（ただし404などのAPIエラーは前述の通り`FAILED`になるはずで、今回の「静かに滞留」という症状とは一致しない）
- **2026-08-16 追加確認**: Gemini APIダッシュボードを確認したところエラーは無く、受信リクエスト数も概ね一致していた。つまり **送信・AI解析自体は成功しており、アプリ側での結果受信〜DB反映の過程で失敗している** と判断できる
- この結果を踏まえた仮説の精緻化: `AnalysisWorker` は `setExpedited`/`setForeground` を使わない素の `CoroutineWorker` で、キューの `while` ループにより **1回のWorker実行内で複数件を順番に処理**している。Androidにはフォアグラウンド化されていないバックグラウンドWorkerに対する実行時間の上限（目安10分程度）があり、超過するとOSが強制停止させる。もし処理中盤（Gemini応答受信後、DB書き込み完了前）でこの停止が起きると、コルーチンがキャンセルされ、`catch` ブロック内の `dao.finishQueue(...)`（suspend関数）自体も実行されずに終わるため、**そのレシートは`RUNNING`のまま何のエラーも記録されず永久に固まる**。`getNextQueuedOrNull()` は `status = 'QUEUED'` のみを対象とするため（`RUNNING`は対象外）、一度固まったレシートは二度と拾われない。一方、その回に処理しきれず`QUEUED`のまま残った分は、次にWorkerが起動したときに新規分と合流して処理件数が増えるため、**送信をまとめて行うほど1回の実行時間が伸び、時間切れに達する確率が上がる**と推測される
- 1枚ずつ送信するテストでは再現しない（1件なら時間内に余裕で終わるため）。まとめて3〜5枚程度連続送信すると再現しやすい可能性がある

### 次のアクション（未着手）

- [ ] 端末の設定でアプリがバッテリー最適化の対象外になっているか確認
- [ ] 滞留中レシートの実際のステータス（PENDING/RUNNING/FAILEDのどれか）をLogcatまたはDB直接確認で切り分け（フィルタ: `AnalysisWorker|AiRequestRouter`）
- [ ] レシートをまとめて3〜5枚連続送信して再現を試みる。試す際は事前に `adb logcat -c` でバッファをクリアしてから `adb logcat -v time > kakeibo_log.txt`等で記録し、`WM-WorkerWrapper|WM-Processor|AnalysisWorker` 周辺のログでWorkManagerによる強制停止（stopWork等）の痕跡を確認する
- [ ] **Logcatは端末のリングバッファ上にしか残らず、数時間〜半日程度で古いログから上書き消去される（端末再起動でも消える）ため、数日前の滞留の原因は事後調査できない**。再発時にすぐ調べられるよう、恒久対策として WorkManager の開始・終了・強制停止の記録をDB（専用ログテーブル、または既存の`analysis_notification_events`等）に永続化しておくことを検討する
- [ ] 上記で原因を特定できたら、必要に応じて1件=1 Workerへの分割・`setExpedited`/フォアグラウンド化・`ExistingWorkPolicy`の見直し・ユーザー向けの手動リトライ導線を検討

### 関連

- [`AGENT_HANDOFF.md`](AGENT_HANDOFF.md) — 2026-08-16 時点でこの課題を理由に新規開発を一旦停止し、実利用しながら様子見中
- 関連コード: `AnalysisWorker.kt`, `ReceiptRepository.kt`（`scheduleAnalysisWork`, `failStaleQueueEntries`, `isQueueEntryStale`）

---

## 参照

- 実装計画: [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md)
- オンボーディング精査 §13: [`plans/onboarding.md`](plans/onboarding.md)
- 外部サービス（Gemini）: `EXTERNAL_SETUP.md`
- Drive 調査（履歴）: `archive/EXTERNAL_SETUP_DRIVE.md`
