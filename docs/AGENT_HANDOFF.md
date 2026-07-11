# エージェント引き継ぎメモ

**最終更新**: 2026-07-11  
**ブランチ**: `main`（7.2' 手動バックアップは作業ツリー未コミット）

---

## いま何をしているか

- **Phase 6 は `main` にマージ済み**（PR #2）。
- **Phase 7.3（APIキーガード）完了**（`9802557`）。
- **Phase 7.2（Drive）は廃止** → **7.2' 手動 JSON バックアップ**に移行（エクスポート・リマインドは実機確認済み、未コミット）。
- **次の実装は Phase 7.1 オンボーディング**（`docs/IMPLEMENTATION_PLAN_REVISED_2026-07-11.md` が現行計画）。

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
| 7.2' | 手動 JSON バックアップ + 月次リマインド（エクスポート・リマインド実機確認済み、未コミット） |

実機確認済み: 6.2, 6.3, 7.2'（エクスポート・月次リマインド）

---

## 次にやること（優先順）

1. **7.2' コミット** — エクスポート・月次リマインドは実機確認済み  
2. **復元フロー実機スモーク**（任意）— 削除 → インポート → 件数確認

3. **Phase 7.1** — 初回オンボーディング（Drive 節なしの軽量ウィザード）

4. **ドキュメント同期** — `REQUIREMENTS.md`, `KNOWN_ISSUES.md` のバックアップ記述

5. **Phase 5 残** — 解析状態可視化、通知履歴永続化、Gemini JSON 改善

## 重要ファイル

| 用途 | パス |
|------|------|
| **現行実装計画** | `docs/IMPLEMENTATION_PLAN_REVISED_2026-07-11.md` |
| バックアップ移行 | `docs/BACKUP_MANUAL_MIGRATION_PLAN.md` |
| 既知の課題 | `docs/KNOWN_ISSUES.md` |
| 要件・ギャップ | `docs/REQUIREMENTS.md`（末尾「実装ギャップ」） |
| 日報（Phase 6） | `docs/daily/2026-06-15.md` |
| デバッグ | `docs/DEBUGGING_GUIDE.md` |

---

## 技術メモ（ハマりどころ）

- **サーバーレス**: Ktor 等のバックエンドなし。Gemini は OkHttp で Android から直呼び。
- **APIキー**: `GeminiApiKeyStore`（EncryptedSharedPreferences）。再送信のみ `ApiKeyMissing` ガードあり。**初回送信は未ガード（7.3）**。
- **解析キュー**: `AnalysisWorker` + `analysis_queue`。キー無し時は現状サイレント終了。
- **ブランチ命名**: `feat/phase5` 等に合わせ、次は `feat/phase7` 想定。
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

`feat/phase6` 主要コミット:

- `d23826e` fix(nav)
- `335098e` fix(notifications) アイコン
- `c3732a9` / `b982ebd` 手動再送信
- `c18533f` 修正画面本格化
- `6ec6775` / `3afa6ce` necessityScore
- `9e8414d` Phase 7 ドキュメント + 日報
