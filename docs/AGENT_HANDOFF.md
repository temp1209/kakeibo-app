# エージェント引き継ぎメモ

**最終更新**: 2026-07-18
**作業ブランチ**: `feat/phase10-multi-ai-provider`（origin 追従済み）  
**ベース**: `main`（Phase 9.5 PR #6 マージ済み。Phase 10 は **未マージ**）

---

## いま何をしているか

**Phase 10（複数 AI / API・フェイルオーバー）は実装・実機確認完了。** PR 作成 → `main` マージが残作業。
その後 **Phase 11（予算・通知・分析 UI）** が次の実装。

| 優先 | Phase | 内容 | 状態 |
|------|-------|------|------|
| ✅ | 9.5 | ブレスト + 要件定義 | PR #6 マージ済 |
| ✅ | **10** | 複数 API・フェイルオーバー | **実装・実機確認完了、PR 待ち** |
| **1** | **11** | 予算・通知・分析グラフ・失敗 UI | 要件定義済み・次実装 |
| — | 5.1 | プロンプトチューニング | 実利用並行 |

**推奨順**: Phase 10 を merge → Phase 11

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

### A. Phase 10 を閉じる

1. `git checkout feat/phase10-multi-ai-provider` && `git pull`
2. `gh pr create` → レビュー → merge
3. 本ファイルと `IMPLEMENTATION_PLAN` を「Phase 10 ✅・マージ済み」に更新

### B. Phase 11 を始める

1. `git checkout main` && `git pull`
2. `git checkout -b feat/phase11-budget-notifications`
3. [`phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md) §6 順:
   - 11.1 `NotificationPrefs` + 設定 UI + Worker ガード
   - 11.2 オンボーディング（予算③・通知文言）
   - 11.3 `BudgetStore`
   - 11.4 分析タブ積み上げ
   - 11.5 予算通知 Worker
   - 11.6 解析失敗 UI
   - 11.7 バックアップ schema

CodeGraph: `codegraph_explore` を先に。`projectPath` にリポジトリルート。
