# エージェント引き継ぎメモ

**最終更新**: 2026-07-11  
**ブランチ**: `feat/phase7`（`origin/main` へ PR マージ待ち）

---

## いま何をしているか

- **Phase 7 完了** — 7.3 / 7.2' / 7.1 いずれも実機確認済み。
- **マージ準備完了** — `feat/phase7` を `origin` に push 済み。`main` は保護ブランチのため **PR 経由でマージ**。
- **次の主戦場**: Phase 5.2（解析状態の可視化統一）。

**後回し（意図的）**

- 7.1 UI ブラッシュアップ → `ONBOARDING_IMPLEMENTATION_PLAN.md` §12
- 7.1 コード精査の改善 → `ONBOARDING_IMPLEMENTATION_PLAN.md` §13、`KNOWN_ISSUES.md` §4

---

## マージ準備チェックリスト（2026-07-11）

| 項目 | 状態 |
|------|------|
| Phase 7 実装 | ✅ |
| 実機確認（7.2' エクスポート・復元・月次、7.1 Wizard） | ✅ |
| `assembleDebug` | ✅ |
| ドキュメント同期 | ✅ |
| コード精査（クリティカルなし） | ✅ |
| ローカルコミット | ✅ |
| `feat/phase7` push | ✅ |
| PR 作成・マージ | ⬜ [`PR を作成`](https://github.com/temp1209/kakeibo-app/pull/new/feat/phase7)（`gh auth login` 後は `gh pr create` 可） |

### PR 用サマリ（コピペ可）

**タイトル**: `Phase 7: APIキーガード、手動バックアップ、初回オンボーディング`

**本文要点**:
- 7.3 送信ガード / 7.2' Drive廃止・手動JSON / 7.1 オンボーディング
- 実機確認済み。ブラッシュアップ・精査改善は後回し（`ONBOARDING_IMPLEMENTATION_PLAN.md` §12–13）

### push 対象コミット（古い順）

```
9802557 feat(phase7): APIキーガードと Drive バックアップ v2
3a3bdc4 refactor(backup): Drive 廃止、手動 JSON バックアップと月次リマインドに移行
3bdd80d docs: 手動バックアップ移行と 07-11 実装計画を追記
8bd7f1a refactor(backup): 月次リマインドを2ボタンに簡素化
b493c16 docs: 月次リマインド UI 簡素化を移行計画に反映
7839258 feat(onboarding): Phase 7.1 初回オンボーディングウィザード
a6cfea0 docs: Phase 7.1 完了とブラッシュアップ延期を記録
```

### push 後

1. 実機で release/debug APK を再インストールしスモーク（任意）
2. Phase 5.2 着手

**注意**: `9802557` に Drive v2 コードが含まれるが、直後の `3a3bdc4` で Drive は削除済み。履歴上の中間状態であり、HEAD では Drive 連携なし。

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
| **現行実装計画** | `docs/IMPLEMENTATION_PLAN_REVISED_2026-07-11.md` |
| 7.1 計画・精査 §13 | `docs/ONBOARDING_IMPLEMENTATION_PLAN.md` |
| バックアップ移行 | `docs/BACKUP_MANUAL_MIGRATION_PLAN.md` |
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
