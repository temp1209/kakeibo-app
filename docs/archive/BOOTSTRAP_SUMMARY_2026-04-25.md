# 実装前ブートストラップまとめ（2026-04-25）

このドキュメントは、実装着手前に行ったセットアップ作業（外部サービス準備・端末準備・GitHub運用整備）を一箇所にまとめたもの。

## 参照ドキュメント
- 要件定義: `REQUIREMENTS.md`
- 外部サービス準備（詳細）: `EXTERNAL_SETUP.md`

## 決定事項（重要）
- **Androidパッケージ名**: `work.temp1209.kakeibo`
- **Gemini連携**: アプリから直接呼び出し（APIキーは初回起動で入力して端末内に安全保管）
- **Driveバックアップ**: Google Drive REST API + OAuth
  - 保存先: **App folder**（`appDataFolder`）
  - スコープ: **`drive.appdata`（最小権限）**
- **開発端末**: Pixel 8a（ワイヤレスデバッグ）
- **GitHub運用**: Private、`main`固定、短命ブランチ→PR→通常マージコミット
- **セキュリティ系機能（Dependabot等）**: 今回はONにしない方針

## 1) 外部サービス準備（完了）
- [x] **Gemini APIキー** 作成
- [x] **Google Cloudプロジェクト** 作成
- [x] **Google Drive API** 有効化
- [x] **OAuth同意画面** 設定
- [x] **テストユーザー** 追加
- [x] **Android OAuthクライアント** 作成（パッケージ名 + debug SHA-1）
- [x] **スコープ** `drive.appdata` を使用する方針を確定

## 2) 端末（Pixel 8a）/ ワイヤレスデバッグ（完了）
- [x] 開発者向けオプションON
- [x] USBデバッグON
- [x] ワイヤレスデバッグON
- [x] `adb devices` で端末が **device** 状態で表示されることを確認

## 3) GitHubリポジトリ整備（完了）
- [x] GitHub Private リポジトリ作成
  - リモート: `https://github.com/temp1209/kakeibo-app.git`
- [x] ローカルをgit初期化し、`main` でpush
- [x] `.gitignore` 追加（keystore / `local.properties` / `.env` 等を除外）
- [x] `README.md` 追加（秘密情報の扱いを明記）
- [x] PRテンプレ追加: `.github/PULL_REQUEST_TEMPLATE.md`
- [x] GitHub Actions（暫定）追加: `.github/workflows/android.yml`
  - Androidプロジェクト作成前は `gradlew` 不在のため、CIは **スキップして成功**するよう調整済み

## 4) GitHubの推奨設定（手動で行う項目）
リポジトリ `Settings` で以下を設定する。

### 4.1 マージ方式を「通常マージ」に固定
`Settings → General → Pull Requests`
- Allow merge commits: **ON**
- Allow squash merging: OFF
- Allow rebase merging: OFF
- Automatically delete head branches: ON（任意だが推奨）

### 4.2 main保護（Branch protection rule）
`Settings → Branches → Branch protection rules (main)`
- Require a pull request before merging: ON
- Require status checks to pass before merging: ON（`Android CI` を必須に）
- Allow force pushes: OFF
- Allow deletions: OFF
- Require approvals: 1人運用ならOFFでOK

## 次にやること（実装開始時）
- Android Studioで新規プロジェクト作成（パッケージ名 `work.temp1209.kakeibo`）
- 最小動線（要件の核）: **起動＝即カメラ → 送信前プレビュー → 送信確定 → キュー投入** から着手

