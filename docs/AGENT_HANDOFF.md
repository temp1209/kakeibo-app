# エージェント引き継ぎメモ

**最終更新**: 2026-07-12  
**作業ブランチ**: `feat/phase8-polish`（PR マージ待ち or マージ直後）  
**ベース**: `main`（Phase 8 PR マージ後は `origin/main` と同期）

---

## いま何をしているか

**Phase 8 品質ブラッシュアップ** は **実装・実機確認完了**。PR マージ後は **実利用 + Phase 5.1 継続チューニング** が主戦場。

| 優先 | タスク | 状態 |
|------|--------|------|
| — | `feat/phase8-polish` → `main` の **PR マージ** | 実施中 |
| — | Phase **8.1〜8.3** | ✅ 実装・実機確認済み |
| — | Phase **2.5** 通知履歴 | ✅ 実機確認済み |
| — | Phase **8.4** 低優先（L1/L7 のみ） | 一部完了、L4/L6 は未着手 |
| **1** | Phase **5.1** プロンプトチューニング | 実利用並行（別 PR） |

詳細計画: [`plans/phase-8-polish.md`](plans/phase-8-polish.md)  
要件: [`REQUIREMENTS.md`](REQUIREMENTS.md) §15  
今日の日報: [`daily/2026-07-12.md`](daily/2026-07-12.md)

---

## Phase 8 実装サマリー（2026-07-12）

| サブ | 内容 | 状態 |
|------|------|------|
| 8.1 | `GeminiJsonViewerSheet` — BottomSheet、pretty-print、コピー | ✅ |
| 8.3 | 権限 M1（ON_RESUME 再評価）/ M2（二重遷移回避） | ✅ |
| 8.2 | ステップインジケータ、typography 統一、権限説明補足 | ✅ |
| 8.4 | L1 `rememberSaveable`、L7 `saveKey` 空文字ガード | ✅ 一部 |

**レビュー後修正**: `autoSkipped*` の saveable 化、JSON 整形のバックグラウンド化、`stepName` 安全パース、`lifecycle-runtime-compose` 追加。

---

## 完了済み（触らなくてよい）

| Phase | 内容 | 実機 |
|-------|------|------|
| 1〜4 | コア（撮影・解析・一覧・分析） | — |
| 6 | 1か月フィードバック対応 | 確認済み |
| 7.3 / 7.2' / 7.1 | APIキーガード、手動 JSON、オンボーディング | 確認済み |
| **5.2** | 解析状態の可視化統一 | 確認済み |
| **2.5** | 通知履歴永続化 | 確認済み |
| **8.1〜8.3** | 品質ブラッシュアップ中核 | 確認済み |

---

## 重要ファイル

| 用途 | パス |
|------|------|
| **引き継ぎ（本ファイル）** | `docs/AGENT_HANDOFF.md` |
| **現行実装計画** | `docs/IMPLEMENTATION_PLAN.md` |
| Phase 8 詳細 | `docs/plans/phase-8-polish.md` |
| JSON 閲覧 | `ui/common/GeminiJsonViewerSheet.kt` |
| オンボーディング | `ui/onboarding/OnboardingWizard.kt` |
| 既知課題 | `docs/KNOWN_ISSUES.md` |

---

## 開発環境

- **IDE**: Android Studio（`JAVA_HOME` = Android Studio JBR）
- **実機**: Pixel 8a（ワイヤレス adb）
- **パッケージ**: `work.temp1209.kakeibo`
- **DB**: Room v5

---

## ユーザー方針

- 応答は **日本語**
- **撮影体験の速さ**を最優先
- **コミット・push は明示依頼時のみ**（本セッションで PR マージまで実施）
- `main` は保護ブランチ → **PR マージ**

---

## 推奨セッション開始手順（マージ後）

1. `git checkout main` && `git pull`
2. レシート実利用 → 困った分類は `docs/fixtures/` に事例追加（5.1）
3. バグのみ小 PR。8.4 残（L4/L6）は必要時のみ
