# バックアップ要件（Google Drive 版・廃止）

> **ステータス**: 廃止（2026-07-11）  
> **置き換え**: 現行は [`REQUIREMENTS.md`](../REQUIREMENTS.md) §12（手動 JSON）および [`plans/backup-manual-migration.md`](../plans/backup-manual-migration.md)

以下は Phase 7.2' 以前の Drive 自動バックアップ要件の記録。実装・コードは削除済み。

---

## 概要（当時）

- **Google Drive**: バックアップ/復元（第一目標）
- **実行タイミング**: 日次自動（当月分のみ）、月初にアーカイブ + フルスナップショット
- **Drive認証**: 端末の Google アカウント + OAuth（`drive.appdata`）
- **復元**: マージ（`updatedAt` 優先）

## バックアップ種別とファイル名

- **当月（上書き）**: `current-month.json`
- **月次アーカイブ**: `archive-YYYY-MM.json`
- **フルスナップショット**: `full-YYYY-MM-01.json`（最新と1つ前のみ保持）

## JSON スキーマ（ルート）

- `backupSchemaVersion`: `"1.0"`
- `exportType`: `"CURRENT_MONTH"` / `"ARCHIVE_MONTH"` / `"FULL_SNAPSHOT"`
- `exportedAt`, `rangeStart`, `rangeEnd`, `app`, `data`
- `data.receipts`, `data.receiptItems`（画像・解析キューは含めない）

詳細フィールド定義は git 履歴の `REQUIREMENTS.md` §12（2026-07-11 以前）を参照。

## 外部設定・トラブルシュート

- OAuth / SHA-1: 旧 `EXTERNAL_SETUP.md` §2, §6（git 履歴）
- 403 調査: `docs/daily/2026-06-16.md`、`KNOWN_ISSUES.md` §2（クローズ）
