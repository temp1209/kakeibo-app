# 既知の課題・バックログ（実利用フィードバック）

実機利用や開発中に気づいた、**未対応または要件に未明文化**の項目を集約する。  
実装タスクの詳細は [`IMPLEMENTATION_PLAN_REVISED_2026-06-16.md`](IMPLEMENTATION_PLAN_REVISED_2026-06-16.md) の **Phase 7** を参照。

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
| **状態** | 調査・修正待ち（バグ） |
| **発見** | 2026-06-16 実利用（開発環境再構築後） |

### 症状

- 設定画面で **Google ログインは成功** する（アカウント表示される）
- **今すぐバックアップ** 実行時に **パーミッションエラー（403 等）** で失敗する

### 想定される原因（優先度順）

1. **debug.keystore の SHA-1 変更**（PC 載せ替え・再インストール後）  
   → Google Cloud の Android OAuth クライアントに **現在の SHA-1 が未登録**
2. OAuth 同意画面の **テストユーザー** にログインアカウントが含まれていない
3. **Drive API 未有効化** または `drive.appdata` スコープ未付与
4. サインイン時に `drive.appdata` スコープがユーザーに拒否された（再ログインで解消する場合あり）

### 関連コード

- `GoogleSignInHelper.kt` — `requestScopes(Scope(DRIVE_APPDATA_SCOPE))`
- `DriveBackupOrchestrator.kt` / `DriveBackupRepository.kt`
- `SettingsScreen.kt` — ログイン成功後に `runScheduledBackup`、失敗は Snackbar
- `DriveBackupWorker.kt` — HTTP 403 を `Result.success()` で握りつぶす（**ユーザーに失敗が伝わりにくい**）

### 調査手順

- `DEBUGGING_GUIDE.md` §8、`EXTERNAL_SETUP.md` §6 を参照
- Logcat で `DriveHttpException` / OkHttp レスポンス本文を確認
- `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore` で SHA-1 を再取得し Console と照合

---

## 3. API キー未設定時、レシート送信で理由が表示されない

| 項目 | 内容 |
|------|------|
| **状態** | 修正待ち（UX バグ） |
| **発見** | 2026-06-16 実利用 |

### 症状

- Gemini API キーが **未設定** の状態でプレビュー画面から **送信確定** できる
- 送信後はカメラに戻るが、**解析が進まない**／失敗理由がユーザーに伝わらない
- 設定タブを開くまで「APIキーが必要」と気づけない

### 技術的背景

- `PreviewScreen` / `MainActivity` は送信前に `GeminiApiKeyStore.hasKey()` をチェックしていない
- `enqueueAnalysis` はキー有無に関係なくキュー投入する
- `AnalysisWorker.doWork()` はキー未設定時 **`Result.success()` で即終了**（サイレントスキップ）

```kotlin
// AnalysisWorker.kt（現状）
val apiKey = GeminiApiKeyStore(applicationContext).readKeyOrNull() ?: return Result.success()
```

### 望ましい対応（案）

- [ ] **送信確定前**（プレビュー画面）で API キー未設定ならダイアログ + 設定タブへ誘導
- [ ] または送信確定時に Snackbar / ダイアログで明示
- [ ] `AnalysisWorker` 側はキー未設定でキューが残り続けないよう、レシートを `FAILED` + エラーメッセージにする（二重防御）
- [ ] Phase 6.2 の再送信と同様、設定誘導の文言を統一

### 関連

- `ReceiptRepository.resendAnalysis()` は `ApiKeyMissing` ガードあり（**再送信のみ対応済み**）
- `PreviewScreen.kt`, `MainActivity.kt`, `AnalysisWorker.kt`, `GeminiApiKeyStore.kt`

---

## 参照

- 実装計画 Phase 7: `IMPLEMENTATION_PLAN_REVISED_2026-06-16.md`
- 外部サービス・SHA-1: `EXTERNAL_SETUP.md`
- Drive 403 調査: `DEBUGGING_GUIDE.md` §8
