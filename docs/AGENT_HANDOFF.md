# エージェント引き継ぎメモ

**最終更新**: 2026-07-14  
**作業ブランチ**: `feat/phase10-multi-ai-provider`  
**ベース**: `main`（Phase 9.5 PR #6 マージ済み）

---

## いま何をしているか

**Phase 10** — 複数 AI / API・フェイルオーバー **実装完了**（実機確認待ち）。

| 優先 | Phase | 内容 | 状態 |
|------|-------|------|------|
| ✅ | **10** | 複数 AI / API・フェイルオーバー | **実装完了・実機確認へ** |
| **1** | **11** | 予算・通知・分析グラフ・失敗 UI | 要件定義済み・**次に実装** |
| — | 5.1 | プロンプトチューニング | 実利用並行 |

**推奨実装順**: Phase 10（実機確認）→ Phase 11

---

## ドキュメント

| Phase | パス |
|-------|------|
| 9.5 ブレスト | [`plans/phase-9.5-brainstorm.md`](plans/phase-9.5-brainstorm.md) |
| 10 複数 API | [`plans/phase-10-multi-ai-provider.md`](plans/phase-10-multi-ai-provider.md) |
| 11 予算・通知 | [`plans/phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md) |

---

## 推奨セッション開始手順

1. `feat/phase10-multi-ai-provider` で実機確認（下記チェックリスト）後 PR
2. Phase 11 着手: `feat/phase11-budget-notifications` を `main` から作成
3. `NotificationPrefs` + オンボーディング予算ステップから（phase-11 ドキュメント §6）
