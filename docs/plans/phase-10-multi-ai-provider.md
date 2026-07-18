# Phase 10 複数 AI / API プロバイダ — 要件・実装記録

> **ステータス**: **完了**（PR #7 マージ済み）
> **作成日**: 2026-07-14  
> **実装日**: 2026-07-14〜15  
> **作業ブランチ**: `feat/phase10-multi-ai-provider`  
> **親**: [`phase-9.5-brainstorm.md`](phase-9.5-brainstorm.md)  
> **次**: [`phase-11-budget-notifications.md`](phase-11-budget-notifications.md)

---

## 1. 背景と目的

### 課題（着手前）

- Gemini 単一 API キー + `GeminiClient` 直結
- 無料枠の **quota / 429** で解析が止まると体験が大きく損なわれる
- メッセージ改善（`GeminiUserMessages`）だけでは根本解決にならない

### 目標（達成）

- **複数の API キー（スロット）** を登録し、失敗時に **自動で次へ切り替え**
- ライン①②③（コンパイル・解析・再スコア）が **共通の `AiRequestRouter`** 経由
- 設定で **優先順序** と **キー追加・削除・疎通** を管理

### スコープ外（初版のまま据え置き）

- 同一リクエストのモデルアンサンブル
- プロバイダごとの異なる JSON スキーマ（出力スキーマは共通）
- クラウド側での API キー共有・同期
- OpenAI / Claude 等の第 2 プロバイダ種（インターフェースのみ用意）

---

## 2. 概念モデル（実装どおり）

```
┌─────────────────────────────────────────┐
│  AiRequestRouter                         │
│  - 画像 / テキスト厳格 JSON              │
│  - 有効スロットを順に試行                │
│  - 例外 → 次へ。全滅 → AllAi…Exception │
└─────────────────────────────────────────┘
         │ uses
         ▼
   AiProvider（GeminiAiProvider → GeminiClient）
```

| 概念 | 実装 |
|------|------|
| **AiProvider** | `data/ai/AiProvider.kt` |
| **GeminiAiProvider** | `GeminiClient` の薄いラッパ |
| **ProviderSlot** | `slotId` / `providerId` / `label` / `enabled` |
| **AiProviderStore** | EncryptedSharedPreferences（`secrets`）。メタ+キーは原子 `commit` |
| **GeminiApiKeyStore** | 互換ファサード（オンボーディング・バナー等） |
| **AiRequestRouter** | 解析・コンパイル・再スコア・疎通 |

### フェイルオーバー方針（確定）

| 結果 | 次へ切替 |
|------|----------|
| プロバイダ呼び出しが **正常完了**（例外なし） | ❌（その応答を採用） |
| **上記以外の例外**（HTTP 4xx/5xx・タイムアウト・キー無効など） | ✅ |
| JSON パース失敗・スキーマ不一致 | ❌（HTTP 成功後。呼び出し側で失敗確定） |

---

## 3. プロバイダ

| 優先 | 候補 | 状態 |
|------|------|------|
| 必須 | **Gemini**（`gemini-2.5-flash`） | ✅ 実装 |
| 高 | **Gemini 複数キー**（複数スロット） | ✅ 実装 |
| 中 | OpenAI 互換 | ⬜ 未着手（`AiProvider` 追加で拡張可） |
| 低 | Claude / その他 | ⬜ |

---

## 4. 機能（as-built）

### 4.1 保存

- `AiProviderStore`: `ai_provider_slots_json` + `ai_provider_ordered_slot_ids` + `ai_api_key_<slotId>`
- レガシー `gemini_api_key` → 初回読取時にスロット 1 件「メイン」へマイグレーション
- `orderedSlots()` / `orderedEnabledSlots()`: order に欠ける ID は **末尾補完**（ルータから消えない）
- バックアップ JSON: **API キーは含めない**（設定画面に注記）。`exportPublicMeta()` で label/順序のみ可

### 4.2 設定 UI（`AiProviderSlotsSection`）

| 要素 | 実装 |
|------|------|
| スロット一覧 | ラベル・プロバイダ名・番号 |
| 順序 | **↑↓ 矢印** およびハンドル **長押しドラッグ** |
| 追加 | 「APIキーを追加」→ ダイアログ（ラベル + キー） |
| 削除 | 確認ダイアログ |
| 疎通 | スロット単位 |
| やらない | キー更新・有効トグルの細かい編集（追加と削除で十分） |

オンボーディング: `GeminiApiKeyStore.saveKey` → `savePrimaryKey`  
- スロット 0 → 「メイン」新規  
- 1 件 → そのスロット更新  
- 複数 → ラベル「メイン」を優先（並び替え後に先頭ダミーを誤上書きしない）

### 4.3 ルータ統合

| 呼び出し元 | 経路 |
|------------|------|
| `AnalysisWorker` | `AiRequestRouter.generateStrictJsonFromImage` |
| `NecessityPolicyCompiler` | `generateStrictJsonFromText`（`ReceiptRepository` 経由） |
| `NecessityRescoreWorker` | 同上 |
| 設定疎通 | `AiRequestRouter.testSlot` |

### 4.4 ログ・エラー

- Logcat タグ: **`AiRequestRouter`** / `AnalysisWorker` / `AiProviderStore`
- 例: `route start order=ダミー → メイン` → `failover from slot=...` → `success ... attempt=2/2`
- 成功ログ `routed via slot=メイン` は **成功したスロットのみ**（先頭失敗後の切替後でもそう見える）
- 全滅: `AllAiProvidersFailedException`。cause が 429 ならユーザー向けに利用上限メッセージ

### 4.5 テスト

- `app/src/test/.../data/ai/AiRequestRouterTest.kt`（実ルータ + `resolveSlots` 注入）

---

## 5. 実装ステップ（実績）

| 順 | 内容 | 状態 |
|----|------|------|
| 10.1 | `AiProvider` + `GeminiAiProvider` | ✅ |
| 10.2 | `AiProviderStore` + 複数スロット・順序 | ✅ |
| 10.3 | `AiRequestRouter` + 例外時フェイルオーバー | ✅ |
| 10.4 | 設定 UI | ✅ |
| 10.5 | レガシー移行 + バックアップ方針（キー除外） | ✅ |
| 10.6 | 実機確認 | ✅ 2026-07-18、問題なし |

**完了条件**

- [x] スロット 2 件・1 件目失敗で 2 件目成功（単体テスト + 実機で確認可能）
- [x] コンパイル・再スコアも同ルータ
- [x] 全スロット失敗時の分かりやすいエラー
- [x] 既存単一キーのマイグレーション
- [x] PR #7 マージ

---

## 6. 主要コードパス

| パス | 役割 |
|------|------|
| `data/ai/AiProvider.kt` | インターフェース |
| `data/ai/GeminiAiProvider.kt` | Gemini 実装 |
| `data/ai/AiRequestRouter.kt` | フェイルオーバー |
| `data/ai/AiRouting.kt` | `AllAiProvidersFailedException` / `AiRoutedResult` |
| `data/ai/ProviderSlot.kt` | スロット・順序 |
| `data/prefs/AiProviderStore.kt` | 永続化 |
| `data/prefs/GeminiApiKeyStore.kt` | 互換ファサード |
| `ui/settings/AiProviderSlotsSection.kt` | 設定 UI |
| `data/gemini/GeminiClient.kt` | 低レベル HTTP（変更最小） |

---

## 7. リスク（残）

| リスク | 対策・現状 |
|--------|------------|
| 他社プロバイダ未対応 | 初版は Gemini 複数キーのみ |
| キー漏洩 | バックアップから除外 |
| フェイルオーバー遅延 | 失敗時のみ次へ |
| 長押しドラッグと縦スクロールの競合 | ↑↓ 矢印を併用 |

---

## 8. 参照

- ブレスト: [`phase-9.5-brainstorm.md`](phase-9.5-brainstorm.md) §0
- 引き継ぎ: [`../AGENT_HANDOFF.md`](../AGENT_HANDOFF.md)
- エラー文言: `GeminiUserMessages`
