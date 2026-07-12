# エージェント引き継ぎメモ

**最終更新**: 2026-07-12  
**作業ブランチ**: `feat/phase9-necessity-policy`  
**ベース**: `main`（Phase 8 PR #4 マージ済み）

---

## いま何をしているか

**Phase 9 必須度ポリシー** — 要件定義完了。**実装未着手**。

| 優先 | タスク | 状態 |
|------|--------|------|
| **1** | Phase **9** 必須度ポリシー実装 | 要件確定・実装待ち |
| — | Phase **5.1** プロンプトチューニング | 実利用並行（別 PR） |

詳細計画: [`plans/phase-9-necessity-policy.md`](plans/phase-9-necessity-policy.md)  
要件: [`REQUIREMENTS.md`](REQUIREMENTS.md) §16

---

## Phase 9 要件サマリー（合意済み）

- **ハイブリッド**: プリセット土台はアプリ内テンプレ、**ユーザー固有部分のみ**コンパイル AI が生成
- **コンパイル**: 設定 **保存時のみ**（訂正例追加だけでは自動実行しない）
- **訂正例**: 案1 — 未コンパイルはバナー表示、解析は直前の有効方針を使用
- **表示**: 1行要約 + ボタンで全文（`promptBlock`）
- **再スコア**: 当月分・1ジョブ・明細 **50件/リクエスト**上限で API 分割
- **バックアップ**: `purposeId`・訂正例・`compiledPolicy` を JSON に含める

**三つの API ライン**: ①方針コンパイル ②レシート解析 ③当月再スコア

---

## 完了済み（触らなくてよい）

Phase 1〜8（コア、7.x、5.2、2.5、8.1〜8.3）— 詳細は [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md)

---

## 重要ファイル

| 用途 | パス |
|------|------|
| **Phase 9 計画** | `docs/plans/phase-9-necessity-policy.md` |
| 現行スコアルール | `data/analysis/AnalysisWorker.kt` |
| 設定画面 | `ui/settings/SettingsScreen.kt` |
| バックアップ | `data/backup/BackupJsonModels.kt` |

---

## 推奨セッション開始手順

1. `git checkout feat/phase9-necessity-policy`
2. [`plans/phase-9-necessity-policy.md`](plans/phase-9-necessity-policy.md) を読む
3. 9.1（データモデル・プリセットテンプレ）から実装
