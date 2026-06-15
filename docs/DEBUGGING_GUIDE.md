# デバッグガイド（開発効率を上げる）

このドキュメントは、このプロジェクト（Compose / 実機Pixel 8a / Gemini / WorkManager想定）で効率よくデバッグするための要点をまとめたもの。

## 1) 実機＋Logcatを“最短ループ”にする
- 常に **Pixel 8a** をRun/Debug対象にする（ワイヤレス接続済み前提）。
- Android Studioの **Logcat** は以下を徹底してノイズを減らす。
  - 対象アプリの **Processだけ**に絞る（アプリ/プロセス選択）。
  - 例外時は `FATAL EXCEPTION` から追う。
- 追跡が必要な箇所だけ `Log.d("TAG", "...")` で前後関係を残す（入れすぎない）。

## 2) ブレークポイント（止めて原因を掘る）
- 「止める」デバッグは、UIよりも **ビジネスロジック層**（ViewModel / UseCase / Repository）に置くと効率が良い。
- 特にブレークポイントが効く対象
  - Geminiの **JSONパース** / スキーマ検証
  - カテゴリ・必須度スコアの **変換/整形**
  - 解析キューの **状態遷移**（PENDING/RUNNING/DONE/FAILED）

## 3) Compose特有の見方
- 表示崩れや状態の流れは **Layout Inspector** で確認する（Composeツリー/パラメータ）。
- 再描画が多い・意図せず更新される場合は、まず **状態（State/Flow）を更新している箇所**を追う。

## 4) “再現を固定する”デバッグ（入力の揺れをなくす）
レシート画像やGeminiレスポンスは揺れやすいので、再現性を上げる。

- 固定の **テスト画像（数枚）** を用意し、常に同じ入力で検証する。
- Geminiの **生JSONを保存**し、UI/DB側はそのJSONを再読み込みして再現できるようにする（要件とも整合）。

## 5) バックグラウンド処理（WorkManager想定）のデバッグ
- 開発中は検証用に「今すぐ実行」導線（ボタン/隠しメニュー等）があると速い。
- 端末の **バッテリー最適化**が原因で止まり得る（必要なら除外設定）。

## 6) 例外・クラッシュを最速で拾う運用
- 初期は `try/catch` で握りつぶさず、**落として原因を見る**方が速い。
- 安定してきたら、ユーザー向けにはエラー表示、内部的にはログ＋状態遷移を整理する。

## 7) 開発中のデータ退避（リリース機能ではない）

大きな改修の前に、実機データを PC へ取り出しておく手順。**アプリ UI には出さない**（自分用 debug 運用向け）。

### 方法 A: adb で DB を pull（手軽・全データ）

1. Pixel 8a を adb 接続（`adb devices` で表示されること）
2. リポジトリルートで:

```powershell
.\scripts\dev-pull-device-data.ps1
```

3. `backups/dev/YYYY-MM-DD_HHmmss/kakeibo.db` ができる（画像があれば `receipts/` も）

- **debug ビルド**のみ `run-as work.temp1209.kakeibo` が使える
- レシート画像は内部ストレージ `files/receipts/`（40日保持）。スクリプトは tar 対応端末なら一緒に退避を試みる
- 復元は手動で DB を戻すより、下記 Drive JSON の方が安全

### 方法 B: Drive バックアップ（JSON・取引データのみ）

1. アプリ **設定** → **今すぐバックアップ**（Google ログイン済み）
2. JSON は Drive の **appDataFolder**（ユーザーからは見えない隠し領域）に保存される
3. 万が一の復元はアプリの **Driveから復元（マージ）**

画像は JSON に含まれない（要件どおり）。画像まで残すなら方法 A を併用。

### おすすめ（改善着手前）

1. `dev-pull-device-data.ps1` で DB（＋可能なら画像）を PC に保存
2. あわせて設定の **今すぐバックアップ** も実行
3. `backups/dev/` を ZIP して別場所にコピー

`backups/` は `.gitignore` 済み想定（コミットしない）。

**`.db` の見方**: SQLite のバイナリファイル。テキストエディタでは文字化けする。先頭が `SQLite format 3` なら正常。[DB Browser for SQLite](https://sqlitebrowser.org/) 等で開く。

```powershell
# 実行ポリシーで止まる場合
powershell -ExecutionPolicy Bypass -File .\scripts\dev-pull-device-data.ps1
```

## 8) Google Drive バックアップが 403 / パーミッションエラーになる

**症状**: 設定で Google ログインは成功するが、「今すぐバックアップ」で失敗する（Snackbar に permission / 403 等）。

### 切り分けチェックリスト

1. **debug.keystore の SHA-1**（PC 載せ替え後はほぼ必須）
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   ```
   - [Google Cloud Console](https://console.cloud.google.com/) → 認証情報 → **Android OAuth クライアント**（`work.temp1209.kakeibo`）に上記 SHA-1 が登録されているか
   - 詳細: `EXTERNAL_SETUP.md` §6

2. **OAuth 同意画面のテストユーザー**に、ログインした Google アカウントが含まれているか

3. **Google Drive API** がプロジェクトで有効か

4. **スコープ** `drive.appdata` がサインイン時に付与されているか  
   - 対策: 設定でサインアウト → 再ログイン（スコープ同意を再表示）

### Logcat

- タグ・例外: `DriveHttpException`, OkHttp のレスポンス code / body
- `DriveBackupWorker` は現状 **403 を success 扱い**する箇所あり → UI に失敗が出ない場合は Worker ログも確認（`KNOWN_ISSUES.md` §2）

### コード上の参照

- `GoogleSignInHelper.kt` — `DRIVE_APPDATA_SCOPE`
- `DriveBackupRepository.kt` — REST 呼び出し
- `SettingsScreen.kt` — 手動バックアップの Snackbar

