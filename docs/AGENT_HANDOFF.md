# エージェント引き継ぎメモ

**最終更新**: 2026-07-12  
**作業ブランチ**: `feat/phase9-necessity-policy`  
**ベース**: `main`（Phase 8 PR #4 マージ済み）

---

## いま何をしているか

**Phase 9 必須度ポリシー** — **実装完了**。残りは実機スモークと PR マージ。

| 優先 | タスク | 状態 |
|------|--------|------|
| **1** | Phase **9** 実機確認 → PR | 実装済み・実機待ち |
| — | Phase **5.1** プロンプトチューニング | 実利用並行（別 PR） |
| — | `NecessityPresetTemplates` 境界ケース数値 | 実利用しながら調整 |

詳細計画: [`plans/phase-9-necessity-policy.md`](plans/phase-9-necessity-policy.md)  
要件: [`REQUIREMENTS.md`](REQUIREMENTS.md) §16

---

## Phase 9 実装サマリー

- **ハイブリッド**: プリセット土台は `NecessityPresetTemplates`、ユーザー固有はコンパイル AI
- **三 API ライン**: ①コンパイル ②解析 ③当月再スコア
- **プロンプト**: `data/prompt/` に集約（`ReceiptAnalysisPrompt` + `necessity/*`）
- **プリセット別**: `scoreBands` / `boundaryCases` / マージ調整幅注釈
- **訂正例**: 修正画面保存時に自動追加、コンパイル確認後クリア
- **バックアップ**: `backupSchemaVersion` 1.2、`necessityPolicy` 同梱

---

## 重要ファイル

| 用途 | パス |
|------|------|
| **Phase 9 計画** | `docs/plans/phase-9-necessity-policy.md` |
| 解析プロンプト | `data/prompt/ReceiptAnalysisPrompt.kt` |
| プリセット・境界ケース | `data/prompt/necessity/NecessityPresetTemplates.kt` |
| コンパイル | `data/necessity/NecessityPolicyCompiler.kt` |
| 設定 UI | `ui/settings/NecessityPolicySection.kt` |
| バックアップ | `data/backup/BackupJsonModels.kt` |

---

## 推奨セッション開始手順

1. `git checkout feat/phase9-necessity-policy`
2. [`plans/phase-9-necessity-policy.md`](plans/phase-9-necessity-policy.md) §8 テスト手順で実機確認
3. 問題なければ PR 作成・マージ
