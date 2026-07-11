# ドキュメント索引

`docs/` の入口。迷ったらここから。

## よく使う（現行）

| ファイル | 用途 |
|----------|------|
| [`REQUIREMENTS.md`](REQUIREMENTS.md) | 要件定義 |
| [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) | **現行**実装計画（2026-07-11 版） |
| [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) | 既知の課題・バックログ |
| [`AGENT_HANDOFF.md`](AGENT_HANDOFF.md) | エージェント / 開発再開時の引き継ぎ |
| [`DEBUGGING_GUIDE.md`](DEBUGGING_GUIDE.md) | 実機デバッグ手順 |
| [`EXTERNAL_SETUP.md`](EXTERNAL_SETUP.md) | Gemini API 等の外部設定 |

## フェーズ別の詳細計画（完了済み）

| ファイル | 内容 |
|----------|------|
| [`plans/onboarding.md`](plans/onboarding.md) | Phase 7.1 オンボーディング（§13 にコード精査メモ） |
| [`plans/backup-manual-migration.md`](plans/backup-manual-migration.md) | Drive 廃止 → 手動 JSON バックアップ移行 |

## その他

| パス | 内容 |
|------|------|
| [`daily/`](daily/) | 開発日報（歴史的記録。パスは当時のまま） |
| [`fixtures/`](fixtures/) | 回帰テスト用フィクスチャ |
| [`archive/`](archive/) | 旧版実装計画・起動時メモ |

## ディレクトリ構成

```
docs/
├── README.md              ← このファイル
├── REQUIREMENTS.md
├── IMPLEMENTATION_PLAN.md ← 現行計画
├── KNOWN_ISSUES.md
├── AGENT_HANDOFF.md
├── DEBUGGING_GUIDE.md
├── EXTERNAL_SETUP.md
├── plans/                 ← 完了したフェーズの詳細計画
├── daily/                 ← 日報
├── fixtures/              ← テスト素材
└── archive/               ← 旧版ドキュメント
```
