# 外部サービス準備メモ

このファイルは、実装前に行った「アカウント登録/コンソール設定/端末設定」を将来忘れないための記録。

> **2026-07-11**: Google Drive 自動バックアップは廃止。Drive 関連の設定手順は [`archive/EXTERNAL_SETUP_DRIVE.md`](archive/EXTERNAL_SETUP_DRIVE.md)（履歴）。

## 決定事項

- **配布形態**: 自分用（デバッグビルド/ローカル運用のみ）
- **Gemini呼び出し**: アプリから直接呼び出し（サーバなし）
- **データ保全**: 手動 JSON エクスポート/インポート（SAF）。月次リマインドは一覧タブ
- **Androidパッケージ名**: `work.temp1209.kakeibo`

## 1) Gemini（APIキー）

- [x] **APIキーを作成**（プロジェクト作成が必要だったため作成して発行）
- [x] **課金/クォータ/上限**を確認（予算アラート/クォータ上限が設定できるなら設定）
- [ ] **APIキーの保管場所**を決める（パスワードマネージャ推奨）
  - 注意: リポジトリへコミットしない/コードへ直書きしない
- [x] **初回入力導線**: オンボーディング Wizard + 設定タブ（Phase 7.1）
- [x] **未設定時ガード**: 送信確定時ダイアログ + Worker `FAILED`（Phase 7.3）

## 2) デバッグ署名（debug.keystore）

- [x] **debug.keystore のSHA-1を取得**（Drive 調査時に使用。現行バックアップでは不要）
  - PowerShellでは `%USERPROFILE%` が展開されない場合があるため、`$env:USERPROFILE` を使用

## 3) 端末（Pixel 8a）/ ワイヤレスデバッグ

- [x] **開発者向けオプション**を有効化
- [x] **USBデバッグ**を有効化
- [x] **ワイヤレスデバッグ**を有効化
- [x] **PCから接続**（`adb pair` → `adb connect` 相当の設定）
- [x] **接続確認**（`adb devices` で端末が見える）
- [x] **通知テスト準備**（通知許可）
- [x] **バッテリー最適化**（必要なら除外設定）: WorkManagerが止まりにくいように調整

## 4) 実装に入る前に「手元に揃っている」こと

- [ ] Gemini **APIキー**（入力用）
- [ ] Pixel 8a が **ワイヤレスでadb接続**できる

## 5) Firebase App Distribution（push→自動配布）

出先で Claude Code から push した後、Android Studio を開かなくても Pixel 8a 側に更新通知が来て
ワンタップでインストールできるようにするための設定。`.github/workflows/distribute.yml` が
push のたびに debug APK をビルドし、Firebase App Distribution 経由でテスター（自分）に配布する。
完全な無音更新は Android の仕組み上不可能（root/EMM登録なしでは）なため、
「通知→ワンタップでインストール」までが現実的な最終形。

- [ ] **Firebaseプロジェクトを作成**（<https://console.firebase.google.com/> 、無料のSparkプランで可）
- [ ] **Androidアプリを登録**（パッケージ名 `work.temp1209.kakeibo`。google-services.json は今回不要、登録のみでOK）
- [ ] **App Distributionを有効化**（Firebaseコンソール左メニュー → Release & Monitor → App Distribution）
- [ ] **テスターグループ「self」を作成**し、自分のGoogleアカウントのメールアドレスを追加（このファイルには書かない。パスワードマネージャ等で管理）
- [ ] **サービスアカウントを作成**（Google Cloud Console → IAM と管理 → サービスアカウント、対象はFirebaseプロジェクトと同じGCPプロジェクト）
  - ロールは「Firebase App Distribution Admin」（無ければ「編集者」）を付与
  - JSONキーを発行してダウンロード
- [ ] **Firebaseコンソールで「アプリID」を確認**（プロジェクトの設定 → 全般 → 登録したAndroidアプリの「アプリID」。`1:xxxxxxxx:android:xxxxxxxx` の形式）
- [ ] **GitHubリポジトリにSecretsを登録**（Settings → Secrets and variables → Actions）
  - `FIREBASE_SERVICE_ACCOUNT_JSON`: ダウンロードしたサービスアカウントJSONの中身をそのまま貼り付け
  - `FIREBASE_APP_ID`: 上記で確認したアプリID
- [ ] **Pixel 8aに「Firebase App Tester」アプリをインストール**し、テスターに登録したGoogleアカウントでログイン
- [ ] **動作確認**: 何かpushしてGitHub Actionsの `distribute` ジョブが成功することを確認 → Pixel 8aに通知が来るか確認

### 運用メモ

- 配布されるのは常に **debugビルド**（署名鍵の管理が不要で、今の「自分専用サイドロード」方針と合う）。Play Store 提出用のreleaseビルド署名は別途検討（`docs/PLAY_STORE_PUBLICATION_DECISION.md` 参照）。
- `versionCode` はCI実行ごとに `github.run_number` で上書きされる（`app/build.gradle.kts` の `versionCodeOverride` プロジェクトプロパティ）。ローカルの `./gradlew assembleDebug` では従来どおり `versionCode = 1` のまま。
- トリガーは全ブランチへの push（`.github/workflows/distribute.yml`）。作業ブランチへpushした時点で配布されるので、mainへのマージを待つ必要はない。

## 6) トラブルシューティング

### Gemini API キー未設定でレシート送信

- **現状（7.3 以降）**: 送信確定時に理由を表示し設定へ誘導。Worker は `FAILED` + メッセージを記録
- 対策: 設定タブまたはオンボーディングで API キーを保存してから撮影

### 手動 JSON バックアップ

- 設定 → **JSON をエクスポート** / **JSON から復元（マージ）**
- ローカル 0 件ではエクスポート不可
- 実機デバッグ手順: [`DEBUGGING_GUIDE.md`](DEBUGGING_GUIDE.md) §7
