# kakeibo-app

Android向け家計簿アプリ（Kotlin + Jetpack Compose）。

- レシート撮影 → Gemini APIで解析 → 保存 → 一覧/分析
- Google Drive（App folder）へJSONバックアップ

## ドキュメント
- 要件定義: `REQUIREMENTS.md`
- 外部サービス準備メモ: `EXTERNAL_SETUP.md`
- 日報: `docs/daily/`（テンプレート: `docs/daily/TEMPLATE.md`）

## 秘密情報の扱い（重要）
- **APIキー等はコミットしない**（`.gitignore`で除外、コードへ直書き禁止）
- Gemini APIキーは **アプリ初回起動で入力**し端末内で安全に保持する想定

## 開発環境（予定）
- Android Studio
- 実機: Pixel 8a（ワイヤレスデバッグ）

## ライセンス
MIT License — 詳細は [LICENSE](LICENSE) を参照

