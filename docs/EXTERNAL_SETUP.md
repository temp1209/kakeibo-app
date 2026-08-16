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

- [x] **Firebaseプロジェクトを作成**（<https://console.firebase.google.com/> 、無料のSparkプランで可）
- [x] **Androidアプリを登録**（パッケージ名 `work.temp1209.kakeibo`。google-services.json は今回不要、登録のみでOK）
- [x] **App Distributionを有効化**（Firebaseコンソール左メニュー → Release & Monitor → App Distribution）
- [x] **テスターグループ「self」を作成**し、自分のGoogleアカウントのメールアドレスを追加（このファイルには書かない。パスワードマネージャ等で管理）
- [x] **サービスアカウントを作成**（Google Cloud Console → IAM と管理 → サービスアカウント、対象はFirebaseプロジェクトと同じGCPプロジェクト）
  - ロールは「Firebase App Distribution Admin」を付与。GCP Consoleの日本語UIでは「Firebase」で検索してもロールが出てこないことがあるため、見つからない場合は「App Distribution」で検索するか、先に「Firebase App Distribution API」をAPIライブラリで有効化してから探す（「編集者」はプロジェクト内のほぼ全リソースに触れる過剰な権限なので極力避ける）
  - JSONキーを発行してダウンロード
  - ⚠️ **同じGoogleアカウントに複数のFirebase/GCPプロジェクトがある場合、操作対象のプロジェクトを取り違えやすい**（Firebaseで新規プロジェクトを作るたびに裏でGCPプロジェクトが新規作成されるため）。作業前に必ずGCP Console上部のプロジェクト選択で対象を確認すること。取り違えると `distribute` ジョブが `HTTP 403 The caller does not have permission` で失敗する
- [x] **Firebaseコンソールで「アプリID」を確認**（プロジェクトの設定 → 全般 → 登録したAndroidアプリの「アプリID」。`1:xxxxxxxx:android:xxxxxxxx` の形式）
- [x] **GitHubリポジトリにSecretsを登録**（Settings → Secrets and variables → Actions、または `gh secret set` コマンド）
  - `FIREBASE_SERVICE_ACCOUNT_JSON`: ダウンロードしたサービスアカウントJSONの中身をそのまま貼り付け
  - `FIREBASE_APP_ID`: 上記で確認したアプリID
- [ ] **Pixel 8aに「Firebase App Tester」アプリをインストール**し、テスターに登録したGoogleアカウントでログイン
- [x] **動作確認**: 何かpushしてGitHub Actionsの `distribute` ジョブが成功することを確認（2026-08-16 確認済み） → Pixel 8aに通知が来るかは未確認

### 運用メモ

- 配布されるのは常に **debugビルド**（署名鍵の管理が不要で、今の「自分専用サイドロード」方針と合う）。Play Store 提出用のreleaseビルド署名は別途検討（`docs/PLAY_STORE_PUBLICATION_DECISION.md` 参照）。
- **debug署名鍵をCI/ローカルで固定している**（`app/build.gradle.kts` の `signingConfigs.debug` が `app/debug.keystore` を明示参照）。CIランナーは実行のたびに使い捨てで `~/.android/debug.keystore` を毎回自動生成するため、これを指定しないとビルドごとに異なる鍵で署名され、Androidが「別アプリ」とみなしてアップデートではなく**アンインストール→再インストール扱いになりデータが全消去される**（2026-08-16に実利用で発覚、修正済み）
  - `app/debug.keystore` は**このプロジェクト専用に新規生成した鍵**（`keytool -genkeypair`）で、開発機で他プロジェクトと共有しているデフォルト鍵は使わない
  - 秘密鍵の実体を公開リポジトリに置くと、同じ鍵で署名した偽装APKを「正規のアップデート」としてインストールされてしまうリスクがあるため、**`app/debug.keystore` はコミットせず（`.gitignore`で除外）、GitHub Secrets `DEBUG_KEYSTORE_BASE64` に登録**し、CI実行時にのみ復元する（`.github/workflows/distribute.yml` の「Restore debug keystore」ステップ）
  - Secrets登録コマンド: `[System.Convert]::ToBase64String([System.IO.File]::ReadAllBytes("app/debug.keystore")) | gh secret set DEBUG_KEYSTORE_BASE64 --repo temp1209/kakeibo-app`（PowerShell）
  - ローカル開発機では `app/debug.keystore` を一度生成すればそのまま使い続けられる（コミットされないため、リポジトリを新しい端末にcloneした場合は改めて生成が必要）
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
