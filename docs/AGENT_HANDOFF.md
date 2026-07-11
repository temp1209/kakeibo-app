# エージェント引き継ぎメモ

**最終更新**: 2026-07-11  
**ブランチ**: `main`（7.1 コミット前）

---

## いま何をしているか

- **Phase 6 は `main` にマージ済み**（PR #2）。
- **Phase 7 完了** — 7.3 APIキーガード、7.2' 手動バックアップ、7.1 オンボーディング（いずれも実機確認済み）。
- **次の主戦場**: Phase 5.2（解析状態の可視化統一）。

**7.1 ブラッシュアップは後回し** — ウィザードの UI 磨き込みは意図的に延期。`ONBOARDING_IMPLEMENTATION_PLAN.md` §12 参照。

---

## 完了済み（触らなくてよい）

| Phase | 内容 |
|-------|------|
| 6.1 | ナビゲーション（ランチャー→カメラ、タブで子画面 pop） |
| 6.2 | 解析失敗の手動再送信（自動リトライ廃止） |
| 6.3 | 修正画面フル編集（`ExpenseLineEditor` 共通化、`applyReceiptEdit`） |
| 6.4 | necessityScore プロンプト改訂 + 回帰フィクスチャ |
| 6.5 | 通知アイコン `ic_notification.xml` |
| 7.3 | APIキー未設定時の送信ガード |
| 7.2' | 手動 JSON バックアップ + 月次リマインド |
| 7.1 | 初回オンボーディング Wizard + カメラバナー |

実機確認済み: 6.2, 6.3, 7.2'（エクスポート・月次リマインド・復元）, 7.1（Wizard 一通り）

---

## 次にやること（優先順）

1. **`git push`**
2. **Phase 5.2** — 解析状態の可視化統一
3. **7.1 ブラッシュアップ**（低優先・後回し）— ウィザード UI/UX の磨き込み

---

## 重要ファイル

| 用途 | パス |
|------|------|
| **現行実装計画** | `docs/IMPLEMENTATION_PLAN_REVISED_2026-07-11.md` |
| バックアップ移行 | `docs/BACKUP_MANUAL_MIGRATION_PLAN.md` |
| 7.1 オンボーディング | `docs/ONBOARDING_IMPLEMENTATION_PLAN.md` |
| 既知の課題 | `docs/KNOWN_ISSUES.md` |
| 要件・ギャップ | `docs/REQUIREMENTS.md`（§14 オンボーディング、末尾ギャップ表） |
| 日報 | `docs/daily/2026-07-11.md` |
| デバッグ | `docs/DEBUGGING_GUIDE.md` |

### 7.1 新規/変更コード

| ファイル | 役割 |
|----------|------|
| `data/prefs/OnboardingPrefs.kt` | 完了フラグ |
| `ui/onboarding/OnboardingWizard.kt` | 5ステップ Wizard |
| `ui/settings/GeminiApiKeyInputSection.kt` | APIキー入力共通化 |
| `MainActivity.kt` | 初回ゲート、deep link 例外 |
| `CameraScreen.kt` | APIキー未設定バナー |
| `SettingsScreen.kt` | 共通 Composable 利用 |
| `ui/permissions/CameraPermission.kt` | `autoRequest` パラメータ |

---

## 技術メモ（ハマりどころ）

- **サーバーレス**: Ktor 等のバックエンドなし。Gemini は OkHttp で Android から直呼び。
- **APIキー**: `GeminiApiKeyStore`（EncryptedSharedPreferences）。送信時ガード（7.3）+ オンボーディング + カメラバナー。
- **解析キュー**: `AnalysisWorker` + `analysis_queue`。キー無し時は `FAILED`（7.3）。
- **オンボーディング**: 未完了時は NavHost を出さず Wizard のみ。通知 deep link は Wizard スキップ。
- **コミット**: ユーザー明示時のみ。`temp1209` / `75376279+temp1209@users.noreply.github.com`（git config 未設定環境では env で指定）。
- **開発環境**: `JAVA_HOME` = Android Studio JBR。実機 Pixel 8a。データ退避 `scripts/dev-pull-device-data.ps1`。

---

## ユーザー方針

- 応答は **日本語**
- 撮影体験の速さは最優先で維持
- 手動5段階の必須度評価は **不採用**（プロンプト + 修正画面で調整）
- 1か月以上の実利用を前提にした UX 改善

---

## 直近の git（参考）

`main` 直近コミット（push 前の可能性あり）:

- `b493c16` docs: 月次リマインド UI簡素化
- `8bd7f1a` refactor(backup): 月次リマインド2ボタン化
- `3bdd80d` docs: 手動バックアップ移行
- `3a3bdc4` refactor(backup): Drive廃止・手動JSON
- `9802557` feat(phase7): APIキーガードと Drive v2（履歴）

未コミット: 7.2' 残差 + 7.1 オンボーディング一式
