# エージェント引き継ぎメモ

**最終更新**: 2026-07-18
**作業ブランチ**: `feat/phase11-budget-notifications`
**ベース**: `main`（Phase 10 PR #7 マージ済み）

---

## いま何をしているか

**Phase 10（複数 AI / API・フェイルオーバー）は PR #7 でマージ済み。**
**Phase 11（予算・通知・分析 UI）** を実装中。11.1〜11.4 まで完了。

| 優先 | Phase | 内容 | 状態 |
|------|-------|------|------|
| ✅ | 9.5 | ブレスト + 要件定義 | PR #6 マージ済 |
| ✅ | **10** | 複数 API・フェイルオーバー | PR #7 マージ済み |
| **1** | **11** | 予算・通知・分析グラフ・失敗 UI | **11.1〜11.4 完了、実装中** |
| — | 5.1 | プロンプトチューニング | 実利用並行 |

**次**: Phase 11.5 予算通知 Worker

---

## Phase 10 要約（as-built）

- **Gemini 複数スロット**（初版。他プロバイダ種は未追加）
- 例外なら常に次スロットへ。パース失敗はルータ外
- 設定: 追加ダイアログ / 削除 / ↑↓・長押しドラッグ / 疎通。キーの細かい編集 UI は無し
- 詳細: [`plans/phase-10-multi-ai-provider.md`](plans/phase-10-multi-ai-provider.md)

### 実機確認チェックリスト

- [x] 既存単一キーが「メイン」スロット 1 件に見える
- [x] ダミーを 1 番・本番を 2 番 → Logcat **`AiRequestRouter`**: `route start order=...` → `failover` → `success attempt=2/2`
- [x] 本番だけ 1 番 → 切替なしで成功（`attempt=1/1`）
- [x] 両方無効 → 全滅メッセージ
- [x] 方針コンパイル・必須度再スコアも同様に動作
- [x] バックアップ JSON に API キーが含まれない
- [x] オンボーディングのキー保存が「メイン」を壊さない（複数スロット時）

実機確認日: 2026-07-18。特に問題なし。

### Logcat

フィルター例: `AiRequestRouter|AnalysisWorker|AiProviderStore`  
（`Analytics` では出ない）

---

## ドキュメント

| Phase | パス |
|-------|------|
| 9.5 ブレスト | [`plans/phase-9.5-brainstorm.md`](plans/phase-9.5-brainstorm.md) |
| 10 複数 API | [`plans/phase-10-multi-ai-provider.md`](plans/phase-10-multi-ai-provider.md) |
| 11 予算・通知 | [`plans/phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md) |

---

## 推奨セッション開始手順

1. `feat/phase11-budget-notifications` で作業を継続
2. [`phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md) §6 順:
   - 11.1 `NotificationPrefs` + 設定 UI + Worker ガード ✅
   - 11.2 オンボーディング（予算③・通知文言）✅
   - 11.3 `BudgetStore` ✅
   - 11.4 分析タブ積み上げ ✅
   - 11.5 予算通知 Worker ← 次
   - 11.6 解析失敗 UI
   - 11.7 バックアップ schema

CodeGraph: `codegraph_explore` を先に。`projectPath` にリポジトリルート。
