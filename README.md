# kakeibo-app

Android向け家計簿アプリ（Kotlin + Jetpack Compose）。\n\n- レシート撮影 → Gemini APIで解析 → 保存 → 一覧/分析\n- Google Drive（App folder）へJSONバックアップ\n\n## ドキュメント\n- 要件定義: `REQUIREMENTS.md`\n- 外部サービス準備メモ: `EXTERNAL_SETUP.md`\n\n## 秘密情報の扱い（重要）\n- **APIキー等はコミットしない**（`.gitignore`で除外、コードへ直書き禁止）\n- Gemini APIキーは **アプリ初回起動で入力**し端末内で安全に保持する想定\n\n## 開発環境（予定）\n- Android Studio\n- 実機: Pixel 8a（ワイヤレスデバッグ）\n\n## ライセンス\n- Private運用のため、必要になったら追加\n+
