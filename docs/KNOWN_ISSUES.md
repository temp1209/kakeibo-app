# 既知の課題・バックログ（実利用フィードバック）

実機利用や開発中に気づいた、**未対応または要件に未明文化**の項目を集約する。  
実装タスクの詳細は [`IMPLEMENTATION_PLAN_REVISED_2026-07-11.md`](IMPLEMENTATION_PLAN_REVISED_2026-07-11.md) を参照。

最終更新: 2026-07-11

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
- 詳細: [`ONBOARDING_IMPLEMENTATION_PLAN.md`](ONBOARDING_IMPLEMENTATION_PLAN.md)

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
| **詳細** | [`ONBOARDING_IMPLEMENTATION_PLAN.md`](ONBOARDING_IMPLEMENTATION_PLAN.md) **§13** |

優先度の高いもの（権限の ON_RESUME 再チェック、許可時 `onNext` 二重呼び出し整理）は Phase 5.2 後またはブラッシュアップ時に着手。

---

## 参照

- 実装計画: [`IMPLEMENTATION_PLAN_REVISED_2026-07-11.md`](IMPLEMENTATION_PLAN_REVISED_2026-07-11.md)
- オンボーディング精査 §13: [`ONBOARDING_IMPLEMENTATION_PLAN.md`](ONBOARDING_IMPLEMENTATION_PLAN.md)
- 外部サービス・SHA-1（Drive 調査用・履歴）: `EXTERNAL_SETUP.md`
