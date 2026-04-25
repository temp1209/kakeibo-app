# 外部サービス準備メモ（実装前）

このファイルは、実装前に行った「アカウント登録/コンソール設定/端末設定」を将来忘れないための記録。

## 決定事項
- **配布形態**: 自分用（デバッグビルド/ローカル運用のみ）
- **Gemini呼び出し**: アプリから直接呼び出し（サーバなし）
- **Driveバックアップ方式**: Google Drive REST API + OAuth
- **Drive保存先**: `appDataFolder`（App folder方式）
- **Driveスコープ方針**: `drive.appdata`（最小権限）
- **Androidパッケージ名**: `work.temp1209.kakeibo`

## 1) Gemini（APIキー）
- [x] **APIキーを作成**（プロジェクト作成が必要だったため作成して発行）
- [x] **課金/クォータ/上限**を確認（予算アラート/クォータ上限が設定できるなら設定）
- [ ] **APIキーの保管場所**を決める（パスワードマネージャ推奨）
  - 注意: リポジトリへコミットしない/コードへ直書きしない

## 2) Google Drive（Google Cloud Console）
- [x] **Google Cloudプロジェクトを作成**
- [x] **Google Drive API を有効化**
- [x] **OAuth同意画面を設定**
- [x] **テストユーザーに自分のGoogleアカウントを追加**
- [x] **スコープ方針を確定**
  - App folder（`appDataFolder`）運用
  - 最小権限（`drive.appdata`）
- [x] **OAuthクライアントID（Android）を作成**
  - **パッケージ名**: `work.temp1209.kakeibo`
  - **署名SHA-1**: debug.keystore のSHA-1を使用

## 3) デバッグ署名（debug.keystore）
- [x] **debug.keystore のSHA-1を取得**
  - PowerShellでは `%USERPROFILE%` が展開されない場合があるため、`$env:USERPROFILE` を使用
  - debug.keystore の作成日が過去（例: 2024/12/23）でも問題なし（同じPCでデバッグする限りSHA-1は安定）
- [ ] **注意メモ**
  - 別PC/再インストール等で keystore が変わるとSHA-1も変わる → OAuth側にSHA-1を追加登録が必要

## 4) 端末（Pixel 8a）/ ワイヤレスデバッグ
- [x] **開発者向けオプション**を有効化
- [x] **USBデバッグ**を有効化
- [x] **ワイヤレスデバッグ**を有効化
- [x] **PCから接続**（`adb pair` → `adb connect` 相当の設定）
- [x] **接続確認**（`adb devices` で端末が見える）
- [x] **端末にGoogleアカウントをログイン**（Driveバックアップ用）
- [x] **通知テスト準備**（通知許可）
- [x] **バッテリー最適化**（必要なら除外設定）: WorkManagerが止まりにくいように調整

## 5) 実装に入る前に「手元に揃っている」こと
- [ ] Gemini **APIキー**（入力用）
- [ ] Google Cloudの **Android OAuthクライアント作成済み**（`work.temp1209.kakeibo` + debug SHA-1）
- [ ] Pixel 8a が **ワイヤレスでadb接続**できる

