# エージェント引き継ぎメモ

**最終更新**: 2026-07-18
**作業ブランチ**: `main`
**ベース**: `main`（Phase 10 PR #7 / Phase 11 マージ済み）

---

## いま何をしているか

**Phase 10・11 はいずれも `main` にマージ済み。**

| 優先 | Phase | 内容 | 状態 |
|------|-------|------|------|
| ✅ | 9.5 | ブレスト + 要件定義 | PR #6 マージ済 |
| ✅ | **10** | 複数 API・フェイルオーバー | PR #7 マージ済み |
| ✅ | **11** | 予算・通知・分析グラフ・失敗 UI | **完了（`main` マージ）** |
| — | 5.1 | プロンプトチューニング | 実利用並行 |
| — | — | UI/UX ブラッシュアップ | 後続（使わない設定の整理など） |

**次**: 実機での残スモーク（任意）→ UI/UX ブラッシュアップ or 5.1

### Phase 11 要約（as-built）

- 通知マスター + 個別トグル（失敗のみデフォルト ON）
- 月次予算（オンボーディング / 設定 / 分析積み上げ棒）
- 予算進捗通知（10/20/月末・80%/100%、同月内キャッチアップ）
- 解析失敗理由の一覧表示、バックアップ schema 1.3（`budget`）
- `NO_RECEIPT` / `[NO_RECEIPT]` → 解析失敗
- 設定の削減・簡略化は **後続 UI/UX でまとめて実施**（当面は現状維持）

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

1. `main` を最新化して作業ブランチを切る
2. 後続候補: UI/UX ブラッシュアップ（設定項目の整理）、または 5.1 プロンプト
3. Phase 11 詳細: [`phase-11-budget-notifications.md`](plans/phase-11-budget-notifications.md)

CodeGraph: `codegraph_explore` を先に。`projectPath` にリポジトリルート。
