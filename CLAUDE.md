# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

**kakeibo-app** は「レシートを撮るだけで家計簿が完成する」Android向け個人用家計簿アプリ。Kotlin + Jetpack Compose で構築し、レシート撮影 → Gemini API によるマルチモーダル解析 → 構造化データ自動登録、という体験にUXを振り切っている。サーバーレス構成で、Androidアプリから Gemini REST API を直接呼び出す（バックエンドは存在しない）。

- パッケージ名: `work.temp1209.kakeibo`
- `minSdk 26` / `compileSdk`・`targetSdk 36`
- 自分用アプリ（Play Store非公開、デバッグビルド運用が前提）

## よく使うコマンド

```bash
# ビルド
./gradlew assembleDebug

# 全ユニットテスト実行
./gradlew test

# 単体のテストクラスのみ実行
./gradlew test --tests "work.temp1209.kakeibo.data.ai.AiRequestRouterTest"

# Lint
./gradlew lint

# インストルメンテーションテスト（実機/エミュレータ必要）
./gradlew connectedAndroidTest
```

CI（`.github/workflows/android.yml`）は PR / `main` push で `test` → `lint` → `assembleDebug` を実行する。ローカルで変更を確認する際もこの3つを通す。

Kotlin単体テストは `app/src/test/java/work/temp1209/kakeibo/` 配下（JVM実行、`android.util.Log` はモックされないため、Log呼び出しは `runCatching` で握りつぶす実装パターンが使われている: `AiRequestRouter.logD/logW` 参照）。

### 実機デバッグ

開発は Pixel 8a 実機 + ワイヤレスデバッグを前提としている（詳細は `docs/DEBUGGING_GUIDE.md`）。

- Logcatは対象プロセスに絞り、`AiRequestRouter|AnalysisWorker|AiProviderStore` でフィルタするとAI解析まわりの切り替え挙動が追える
- 実機データのPC退避: `scripts/dev-pull-device-data.ps1`（debugビルドの `run-as` でDBをpull）。リリース機能ではなく開発者用
- ユーザー向けバックアップは設定タブの手動JSONエクスポート/インポート（SAF）。Google Driveバックアップは廃止済み（`docs/archive/EXTERNAL_SETUP_DRIVE.md` に履歴のみ）

## アーキテクチャ

パッケージ構成は `data`（Room/ビジネスロジック/外部API）と `ui`（Compose画面、機能ごとにディレクトリ分割）の2層。DIコンテナは無く、各コンポーネントは `Context` を受け取って直接コンストラクトする素朴なスタイル（例: `AppDatabase.get(context)`, `AiProviderStore(context)`）。

### データフロー（撮影→解析→反映）

```
カメラ撮影 → プレビューで送信確定
  → Room DB に ReceiptEntity + ReceiptImageEntity 保存、AnalysisQueueEntity へ投入
  → WorkManager が AnalysisWorker を起動
  → AiRequestRouter 経由で Gemini へ画像+プロンプト送信（厳格JSONスキーマ指定）
  → GeminiStrictParser でレスポンスをパース・検証
  → ReceiptItemEntity（明細・カテゴリ・necessityScore）として保存、GeminiResultEntity に生JSON保存
  → 低信頼(confidence < 0.7)時は needsReview フラグ → 通知タブから修正画面へ
  → OS通知 + 一覧/分析タブへ反映
```

`AnalysisWorker`（`data/analysis/AnalysisWorker.kt`）がこのフローの中心。`analysis_queue` テーブルをポーリングし1件ずつ順次処理する（PENDING/RUNNING/DONE/FAILED/NEEDS_REVIEW の状態遷移）。API未設定・レシート不在(`NO_RECEIPT`)・その他例外はそれぞれ専用のハンドラでFAILED確定し、ユーザー向けメッセージと通知履歴を残す。

### AIプロバイダ層（`data/ai/`）

複数のAPIキー/プロバイダを「スロット」として登録し、順番に試行→通信失敗時は次スロットへフェイルオーバーする設計（Phase 10で導入）。

- `AiProvider` … プロバイダ実装のインターフェース（現状 `GeminiAiProvider` のみ実装）
- `AiProviderRegistry` … providerId → `AiProvider` 実装のマッピング
- `AiProviderStore`（`data/prefs/`）… スロット設定・APIキーの永続化（`EncryptedSharedPreferences`、バックアップJSONには含めない）
- `AiRequestRouter` … `resolveSlots()` で有効スロットを取得し、順に呼び出し。例外は次スロットへフォールバック、パース失敗はルータの外側（呼び出し側）の責務
- テスト時は `AiRequestRouter(resolveSlots = {...})` のコンストラクタで `AiProviderStore` を注入せず直接スロットを渡せる（`AiRequestRouterTest.kt` 参照)

新しいAIプロバイダを追加する場合は `AiProvider` を実装し `AiProviderRegistry.defaultProviders()` に登録する。

### Gemini解析（`data/gemini/`, `data/analysis/`, `data/prompt/`）

- `ReceiptJsonSchema.schemaV1()` … Gemini `responseJsonSchema` に渡す厳格スキーマ定義
- `ReceiptAnalysisPrompt.buildFromStore(...)` … プロンプト本文を構築。必須度ポリシー（`data/necessity/`, `data/prompt/necessity/`）をプロンプトに埋め込む
- `GeminiResponseParser` … Gemini REST レスポンスからテキストを抽出
- `GeminiStrictParser` … 抽出したJSON文字列をドメインモデル（`ReceiptItem` 等）にパース。`[NO_RECEIPT]` はレシート非検出として専用例外 `NoReceiptInImageException` を投げる
- 生のGemini JSONは `GeminiResultEntity` に必ず保存する（再現性確保のため。`docs/DEBUGGING_GUIDE.md` 参照）

### 必須度スコア（necessity）

買い物の「必須/裁量」を `necessityScore`（0-100）としてAIに付与させ、分析タブの積み上げグラフや無駄遣い候補抽出に使う。ユーザーがポリシーをカスタムできる仕組みが `data/necessity/`（`NecessityPolicyCompiler`, `CompiledNecessityPolicy`）と `data/prompt/necessity/` にあり、`NecessityRescoreWorker` で既存データの再スコアリングも可能。

### DB（`data/db/`, Room）

`AppDatabase`（version 5）。マイグレーションは `MIGRATION_1_2`〜`MIGRATION_4_5` として手書きSQLで定義されている（`exportSchema = false` のためスキーマファイルは無し）。テーブル追加・カラム追加時は必ず新しい `Migration` を追加すること（破壊的変更・自動マイグレーションは使わない）。

主要テーブル: `receipts`, `receipt_images`, `receipt_items`, `gemini_results`, `analysis_queue`, `analysis_notification_events`。

### バックアップ（`data/backup/`）

手動JSONエクスポート/インポート（SAF）方式。`BackupExportBuilder`/`BackupJsonCodec`/`BackupMerge` でJSON化・マージを行う。schemaバージョンで後方互換を管理（例: 1.3で `budget` フィールド追加）。APIキーなど秘匿情報はバックアップJSONに含めない。画像は含まれない（DB上のメタデータと明細のみ）。

### 予算・通知（`data/budget/`, `data/notifications/`, `ui/notifications/`）

月次予算に対する必須/裁量の進捗を `BudgetNotificationPolicy` で計算し、`BudgetNotificationWorker`（WorkManager）が10日・20日・月末・閾値（80%/100%）通知を発火。解析結果通知（完了/要確認/失敗）は `NotificationHistory` に永続化され、OS通知を出すかは `NotificationPrefs` のマスター/個別トグルで制御。

### 画面構成（`ui/`）

ボトムタブ5つ: 一覧(`ui/list`) / 分析(`ui/analysis`) / カメラ(`ui/camera`, 中央) / 通知(`ui/notifications`) / 設定(`ui/settings`)。ナビゲーションルートは `ui/nav/Routes.kt` の `Route` sealed classで一元管理（タブ本体か子画面かは `tabRouteValues`/`isTabRoute` で判定）。その他: プレビュー確認(`ui/preview`)、要確認修正(`ui/review`)、手入力(`ui/manual`)、レシート詳細(`ui/detail`)、オンボーディング(`ui/onboarding`)、権限リクエスト(`ui/permissions`)。

## 開発時の注意点

- **秘密情報はコミットしない**: Gemini APIキーは `EncryptedSharedPreferences` で端末内保管のみ。コードやテストにキーを直書きしない
- **DBスキーマ変更時は必ずマイグレーションを追加**（`AppDatabase.kt` の version・migration list両方を更新）
- **例外処理は握りつぶさない**: 開発中は例外を落として原因を特定する方針（`docs/DEBUGGING_GUIDE.md` §6）。ユーザー向けにはエラーメッセージ、内部的にはログと状態遷移（PENDING/RUNNING/DONE/FAILED/NEEDS_REVIEW）を明確に扱う
- **再現性の確保**: Gemini解析はレシート画像・APIレスポンスが揺れやすいため、生JSONは必ずDBに保存し、UIはそのJSONを再読込して再現できるようにする
- ドキュメントの入口は `docs/README.md`。現行の実装計画は `docs/IMPLEMENTATION_PLAN.md`、既知課題は `docs/KNOWN_ISSUES.md`、セッション再開時の引き継ぎは `docs/AGENT_HANDOFF.md` を参照
