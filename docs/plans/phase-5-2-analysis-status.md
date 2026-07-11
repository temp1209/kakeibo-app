# Phase 5.2 解析状態の可視化統一 — 実装計画

> **ステータス**: 計画（未実装）  
> **作成日**: 2026-07-11  
> **親計画**: [`IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) §5.2

---

## 1. 背景と目標

### 解決したいこと

| 画面 | 現状 | 問題 |
|------|------|------|
| 一覧 | `needsReview` のみ「要確認」バッジ | `FAILED` / `PENDING` / `RUNNING` が見えない |
| 詳細 | 「解析失敗」「要確認」「解析待ち / 処理中: PENDING」混在 | 内部 enum 値が露出 |
| 通知 | `status: FAILED` 等の英語表示 | 開発者向け表記のまま |

### 完了条件

- [ ] ユーザー向けラベルが **一覧・詳細・通知タブで統一** される
- [ ] 色/コンテナ色が状態ごとに一貫する
- [ ] APIキー未設定による `FAILED`（`GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE`）が分かる
- [ ] 既存の再送信・修正導線は維持する

### スコープ外

- 通知履歴の永続化（Phase 2.5）
- 解析キューの詳細進捗（% 表示等）
- 一覧での全状態フィルタ

---

## 2. 語彙（確定案）

| DB `analysisStatus` | ユーザー向けラベル | 色の目安 |
|---------------------|-------------------|----------|
| `PENDING` | 解析待ち | 中立（surfaceVariant） |
| `RUNNING` | 解析中 | primaryContainer |
| `DONE` | （通常はバッジ非表示。`needsReview` 時のみ下記） | — |
| `FAILED` | 解析失敗 | errorContainer |
| `NEEDS_REVIEW` | 要確認 | errorContainer |

**補足**

- `needsReview == 1` かつ `analysisStatus == DONE` も「要確認」として扱う（現行どおり）
- `FAILED` かつ `analysisErrorMessage` に APIキー文言が含まれる場合も「解析失敗」ラベル（メッセージ本文で区別）

---

## 3. 実装方針

### 3.1 共通ヘルパー（新規）

**パス案**: `app/.../ui/common/AnalysisStatusPresentation.kt`

```kotlin
data class AnalysisStatusPresentation(
    val label: String,
    val showBadge: Boolean,
    val containerColor: Color?,  // null = バッジ非表示
    val contentColor: Color?,
)

fun ReceiptEntity.analysisStatusPresentation(): AnalysisStatusPresentation
```

- `analysisStatus` + `needsReview` + `analysisErrorMessage`（任意）から表示用を算出
- Compose の `MaterialTheme.colorScheme` は Composable 側で解決してもよい（`@Composable fun ...`）

### 3.2 一覧タブ

**パス**: `ReceiptsListScreen.kt`

- 既存の「要確認」バッジを共通ヘルパーに置き換え
- **追加表示**: `FAILED` →「解析失敗」、`PENDING`/`RUNNING` → 小バッジ（任意: 金額行の横に1つまで）
- 優先度: `FAILED` > 要確認 > 解析中 > 解析待ち

### 3.3 詳細画面

**パス**: `ReceiptDetailScreen.kt`

- `解析待ち / 処理中: ${r.analysisStatus}` をラベル表示に変更
- 失敗/要確認カードの見出しを共通ヘルパーと一致させる

### 3.4 通知タブ

**パス**: `NotificationsScreen.kt`

- `status: ${r.analysisStatus}` を日本語ラベルに
- `needsReview` セクションの英語混在を整理
- `FAILED` は通知タブの「最近」にも含めるか要検討（現状 `filter != FAILED`）→ **失敗セクションを追加** するか、最近に含めてラベル統一（推奨: 失敗を「最近」に含め、ラベルで区別）

### 3.5 7.3 との整合

- `AnalysisWorker` / `GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE` は変更しない
- `FAILED` 表示 + エラーメッセージ + 設定誘導（詳細の再送信フロー）は現行維持

---

## 4. 実装フェーズ

### Phase A — 共通ヘルパー（0.25日）

1. `AnalysisStatusPresentation` + 単体テスト（純 Kotlin で label 算出のみ）
2. 全 `analysisStatus` × `needsReview` の組み合わせ表をテスト

### Phase B — 詳細・通知（0.5日）

1. `ReceiptDetailScreen` を共通ヘルパーに寄せる
2. `NotificationsScreen` の raw status 除去、失敗の見え方を決めて実装

### Phase C — 一覧（0.25日）

1. `ReceiptsListScreen` にバッジ追加（FAILED / 解析中 / 解析待ち）
2. バッジ過多にならないよう1行1バッジまで

### Phase D — 実機確認（0.25日）

1. 解析待ち → 実行中 → 完了の遷移
2. APIキー未設定で FAILED → ラベル・メッセージ
3. 低信頼 NEEDS_REVIEW / needsReview バッジ

---

## 5. テスト手順（実機）

1. APIキー設定済みでレシート送信 → 一覧に一時「解析中」→ 完了後バッジ消える
2. APIキー未設定で送信 →「解析失敗」+ 設定誘導メッセージ
3. 低信頼レシート → 一覧・詳細・通知で「要確認」
4. 通知タブに英語の `status:` が残っていないこと

---

## 6. リスク

| リスク | 対策 |
|--------|------|
| 一覧がバッジだらけ | 優先度付きで1バッジに抑える |
| `NEEDS_REVIEW` と `needsReview` の二重系 | ヘルパーで一本化 |
| 通知タブの FAILED 非表示 | 意図的に変更するなら計画どおり「最近」に含める |

---

## 参照

- [`IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md)
- `ReceiptDetailScreen.kt`, `ReceiptsListScreen.kt`, `NotificationsScreen.kt`
- Phase 7.3: `AnalysisWorker.kt`, `GeminiApiKeyStore.kt`
