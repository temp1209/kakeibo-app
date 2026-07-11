# エージェント引き継ぎメモ

**最終更新**: 2026-07-11  
**ブランチ**: `main`（Phase 7 マージ済み・PR #3）

---

## いま何をしているか

- **Phase 7 完了・マージ済み** — 7.3 / 7.2' / 7.1（PR #3）。
- **次の主戦場**: Phase 5.2（解析状態の可視化統一）。
- **ドキュメント**: `docs/` を整理（[`docs/README.md`](README.md) が索引）。

**後回し（意図的）**

- 7.1 UI ブラッシュアップ → `plans/onboarding.md` §12
- 7.1 コード精査の改善 → `plans/onboarding.md` §13、`KNOWN_ISSUES.md` §4

---

## 完了済み（触らなくてよい）

| Phase | 内容 |
|-------|------|
| 6.1〜6.5 | ナビゲーション、再送信、修正画面、necessityScore、通知アイコン |
| 7.3 | APIキー未設定時の送信ガード |
| 7.2' | 手動 JSON バックアップ + 月次リマインド |
| 7.1 | 初回オンボーディング Wizard + カメラバナー |

実機確認済み: 6.2, 6.3, 7.2'（エクスポート・月次リマインド・復元）, 7.1（Wizard 一通り）

---

## 重要ファイル

| 用途 | パス |
|------|------|
| **現行実装計画** | `docs/IMPLEMENTATION_PLAN.md` |
| 7.1 計画・精査 §13 | `docs/plans/onboarding.md` |
| バックアップ移行 | `docs/plans/backup-manual-migration.md` |
| **ドキュメント索引** | `docs/README.md` |
| 既知の課題・バックログ | `docs/KNOWN_ISSUES.md` |
| 要件 | `docs/REQUIREMENTS.md`（§14 オンボーディング） |
| 日報 | `docs/daily/2026-07-11.md` |

---

## 技術メモ（ハマりどころ）

- **APIキー**: `GeminiApiKeyStore` + オンボーディング + 7.3 ガード + Worker `FAILED`
- **オンボーディング**: 未完了時 Wizard のみ。deep link はスキップ
- **バックアップ**: SAF JSON、`FileBackupOrchestrator`。Drive コードは削除済み
- **開発環境**: `JAVA_HOME` = Android Studio JBR。実機 Pixel 8a

---

## ユーザー方針

- 応答は **日本語**
- 撮影体験の速さは最優先
- クリティカルでない改善は後回しでよい（2026-07-11 方針）
