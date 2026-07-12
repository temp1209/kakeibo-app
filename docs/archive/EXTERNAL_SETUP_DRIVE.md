# 外部サービス準備メモ（Drive 版・廃止）

> **ステータス**: 廃止（2026-07-11）  
> **現行**: [`EXTERNAL_SETUP.md`](../EXTERNAL_SETUP.md)（Gemini のみ）

以下は Phase 7.2' 以前の Google Drive OAuth 設定の記録。

---

## 決定事項（当時）

- **Driveバックアップ方式**: Google Drive REST API + OAuth
- **Drive保存先**: `appDataFolder`（App folder方式）
- **Driveスコープ方針**: `drive.appdata`（最小権限）
- **Androidパッケージ名**: `work.temp1209.kakeibo`

## Google Cloud Console

- Google Cloudプロジェクト作成
- Google Drive API 有効化
- OAuth同意画面 + テストユーザー
- OAuthクライアントID（Android）: パッケージ名 + debug.keystore SHA-1

## デバッグ署名（debug.keystore）

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

- 別PC/再インストール等で keystore が変わると SHA-1 も変わる → OAuth 側に SHA-1 を追加登録が必要

## トラブルシューティング: 403 / パーミッションエラー

**症状**: ログイン成功だがバックアップ失敗。

1. 現在の SHA-1 を取得（上記 keytool）
2. Google Cloud Console → 認証情報 → Android OAuth クライアントに SHA-1 を追加
3. OAuth テストユーザー、Drive API 有効化を確認
4. サインアウト → 再ログイン

詳細: `docs/daily/2026-06-16.md`, `KNOWN_ISSUES.md` §2
