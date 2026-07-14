# Phase 10 複数 AI / API プロバイダ — 要件・実装計画

> **ステータス**: **実装完了**（`feat/phase10-multi-ai-provider`）  
> **作成日**: 2026-07-14  
> **実装日**: 2026-07-14  
> **作業ブランチ**: `feat/phase10-multi-ai-provider`  
> **親**: [`phase-9.5-brainstorm.md`](phase-9.5-brainstorm.md)  
> **次**: [`phase-11-budget-notifications.md`](phase-11-budget-notifications.md)

---

## 1. 背景と目的

### 課題

- 現状は **Gemini 単一 API キー** + `GeminiClient` 直結
- 無料枠の **quota / 429** で解析が止まると、ユーザー体験が大きく損なわれる
- メッセージ改善（`GeminiUserMessages`）だけでは **根本解決にならない**

### 目標

- **複数の AI プロバイダ / API キー** を登録し、失敗時に **自動で次へ切り替え**（フェイルオーバー）
- ライン①②③（コンパイル・解析・再スコア）が **共通のルータ** 経由で動作
- ユーザーは設定で **優先順序** と **キー** を管理

### スコープ外（初版）

- 同一リクエストのモデルアンサンブル（複数 AI の結果マージ）
- プロバイダごとに異なる JSON スキーマ（**出力スキーマは共通**を維持）
- クラウド側での API キー共有・同期

---

## 2. 概念モデル

```
┌─────────────────────────────────────────┐
│  AiRequestRouter                         │
│  - 操作種別（解析画像 / テキストJSON）    │
│  - プロバイダチェーンを順に試行           │
│  - 429/quota → 次へ。全滅 → 例外         │
└─────────────────────────────────────────┘
         │ implements
    ┌────┴────┬────────────┐
    ▼         ▼            ▼
 Gemini    (候補2)     (候補3)
 Provider  Provider    Provider
```

| 概念 | 説明 |
|------|------|
| **AiProvider** | 厳格 JSON 生成の共通インターフェース（画像 + テキスト） |
| **ProviderSlot** | 1 プロバイダ + 1 API キー（またはキー ID）の組 |
| **ProviderChain** | ユーザー設定の試行順序（リスト） |
| **AiRequestRouter** | チェーンを辿り、リトライ可能なエラーで次スロットへ |

### フェイルオーバー対象エラー（案）

| エラー | 次へ切替 |
|--------|----------|
| HTTP 429 / quota / RESOURCE_EXHAUSTED | ✅ |
| HTTP 5xx | ✅（要確認） |
| タイムアウト | ✅（要確認） |
| 4xx（キー無効・403 等） | ✅（キー切替で救える場合） |
| パース失敗・スキーマ不一致 | ❌（同プロバイダ再試行は別途。フェイルオーバーしない） |

---

## 3. プロバイダ候補（未確定・要件時に確定）

| 優先 | 候補 | 理由 |
|------|------|------|
| 必須 | **Gemini**（現行） | 既存実装の移行先 |
| 高 | **Gemini 複数キー** | 別 Google アカウントの無料枠をチェーン |
| 中 | **OpenAI 互換**（GPT-4o mini 等） | 画像 + JSON モード対応モデル |
| 低 | Claude / その他 | スキーマ・画像 API の差分調査が必要 |

**初版の最小**: Gemini スロットを **複数登録** できるだけでも効果大。2 プロバイダ種は 10.2 で追加可。

---

## 4. 機能要件

### 4.1 データモデル・保存

```json
{
  "slots": [
    { "slotId": "uuid", "providerId": "GEMINI", "label": "メイン", "apiKey": "...", "enabled": true },
    { "slotId": "uuid", "providerId": "GEMINI", "label": "予備", "apiKey": "...", "enabled": true }
  ],
  "orderedSlotIds": ["...", "..."]
}
```

- 端末内保存（既存 `GeminiApiKeyStore` を **AiProviderStore** に発展 or 共存移行）
- バックアップ: キーは **エクスポート除外** or マスク（セキュリティ要検討）。スロット順序・label のみ同梱可

### 4.2 設定 UI

| 要素 | 要件 |
|------|------|
| スロット一覧 | ラベル、プロバイダ種別、有効/無効、順序（ドラッグ or 上下） |
| キー追加 | プロバイダ選択 + キー入力 + 疎通テスト |
| 疎通テスト | 既存 `testText` 相当を **スロット単位** で実行 |
| 後方互換 | 既存の単一 Gemini キー → スロット 1 件にマイグレーション |

オンボーディングの API キー入力は **第 1 スロット登録** として接続（Phase 11 と調整）。

### 4.3 ルータ統合箇所

| 呼び出し元 | 現状 | 変更後 |
|------------|------|--------|
| `AnalysisWorker` | `GeminiClient.generateStrictJsonFromImage` | `AiRequestRouter` |
| `NecessityPolicyCompiler` | `GeminiClient.generateStrictJsonFromText` | 同上 |
| `NecessityRescoreWorker` | 同上 | 同上 |
| 設定の疎通テスト | `GeminiClient.testText` | スロット指定 or ルータ |

### 4.4 ログ・ユーザー向けフィードバック

- 成功時: どのスロットで成功したかを **デバッグログ**（ユーザー UI は任意）
- 全スロット失敗: 既存 `GeminiUserMessages` を拡張し「すべての API で上限に達しました」等
- Phase 11 と連携: 一覧・詳細に **最後の失敗理由** + 使用したスロット数を表示

---

## 5. 実装フェーズ（案）

| 順 | 内容 | 規模 |
|----|------|------|
| 10.1 | `AiProvider` インターフェース + `GeminiAiProvider`（現行移行） | M |
| 10.2 | `AiProviderStore` + 複数スロット・順序 | S |
| 10.3 | `AiRequestRouter` + 429 フェイルオーバー | M |
| 10.4 | 設定 UI（スロット管理・疎通） | M |
| 10.5 | 既存キーマイグレーション + バックアップ方針 | S |
| 10.6 | 実機（quota 模擬 or 無効キー混在で切替確認） | S |

**完了条件**

- [x] スロット 2 件以上登録し、1 件目が 429 のとき 2 件目で解析成功する（単体テストでフェイルオーバー検証）
- [x] コンパイル・再スコアも同ルータ経由
- [x] 全スロット失敗時に分かりやすいエラー（`AllAiProvidersFailedException`）
- [x] 既存ユーザーの単一キーが移行される（`AiProviderStore` マイグレーション）

### 実装メモ（2026-07-14）

| 項目 | 内容 |
|------|------|
| 抽象化 | `data/ai/AiProvider`, `GeminiAiProvider`, `AiRequestRouter` |
| 保存 | `AiProviderStore`（EncryptedSharedPreferences）。`GeminiApiKeyStore` は互換ファサード |
| 初版プロバイダ | Gemini のみ（複数キー＝複数スロット） |
| バックアップ | APIキーは従来どおり非同梱。設定画面に注記を追加 |

---

## 6. リスク

| リスク | 対策 |
|--------|------|
| プロバイダ間で JSON 品質差 | 初版は Gemini 複数キーに絞る。他社は別サブフェーズ |
| キー漏洩（バックアップ） | キーはバックアップ JSON から除外をデフォルト |
| フェイルオーバーで遅延増 | 失敗時のみ次へ。成功パスは現状と同じ 1 回 |

---

## 7. 参照

- 現行: `GeminiClient.kt`, `GeminiApiKeyStore.kt`
- エラー判定: `GeminiUserMessages.isRateLimited`
- ブレスト: [`phase-9.5-brainstorm.md`](phase-9.5-brainstorm.md) §0
