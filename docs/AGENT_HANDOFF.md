# エージェント引き継ぎメモ

**最終更新**: 2026-07-11（Phase 8 計画策定）  
**ブランチ**: `main`（5.2 / 2.5 実装コミットあり・push 要確認）

---

## いま何をしているか

- **M2 中核完了** — 5.2 解析状態（実機確認済み）、2.5 通知履歴（実装済み・実機確認待ち）。
- **次の主戦場**: **Phase 8 品質ブラッシュアップ** — [`plans/phase-8-polish.md`](plans/phase-8-polish.md)
- **要件**: `REQUIREMENTS.md` §15

**Phase 8 の中身（まとめ）**

| 順 | 内容 |
|----|------|
| 8.1 | Gemini JSON（pretty-print・コピー・BottomSheet）— 小 |
| 8.3 | オンボーディング M1/M2（権限再評価・二重遷移） |
| 8.2 | オンボーディング UI（ステップ表示・文言） |
| 8.4 | §13 低優先（任意） |

**並行**: Phase 5.1 プロンプトチューニング（実利用ドリブン・PR 分離）

---

## 完了済み（触らなくてよい）

| Phase | 内容 |
|-------|------|
| 6.1〜6.5 | ナビゲーション、再送信、修正画面、necessityScore、通知アイコン |
| 7.3 / 7.2' / 7.1 | APIキーガード、手動バックアップ、オンボーディング |
| 5.2 | 解析状態の可視化統一 |
| 2.5 | 通知履歴永続化 |

実機確認済み: 6.2, 6.3, 7.2', 7.1, **5.2**

---

## 重要ファイル

| 用途 | パス |
|------|------|
| **現行実装計画** | `docs/IMPLEMENTATION_PLAN.md` |
| **次タスク詳細** | `docs/plans/phase-8-polish.md` |
| オンボーディング §13 | `docs/plans/onboarding.md` |
| 要件 §15 | `docs/REQUIREMENTS.md` |
| **ドキュメント索引** | `docs/README.md` |

---

## 技術メモ

- **JSON 閲覧**: `ReceiptDetailScreen` → `AlertDialog` + minify JSON（8.1 で差し替え予定）
- **通知履歴**: `NotificationHistory.kt`, `analysis_notification_events`（DB v5）
- **オンボーディング改善**: `OnboardingWizard.kt` §13 M1/M2

---

## ユーザー方針

- 応答は **日本語**
- 撮影体験の速さは最優先
- クリティカルでない改善は Phase 8 にバンドル
