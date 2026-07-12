package work.temp1209.kakeibo.data.necessity

import org.json.JSONObject
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.gemini.GeminiResponseParser
import work.temp1209.kakeibo.data.prefs.NecessityPolicyStore
import java.time.Instant

class NecessityPolicyCompiler(
    private val gemini: GeminiClient = GeminiClient(),
) {

    data class CompileResult(
        val userSummary: String,
        val userRulesBlock: String,
    )

    fun compile(
        apiKey: String,
        purposeId: NecessityPurposeId,
        corrections: List<NecessityCorrection>,
        existingPolicy: CompiledNecessityPolicy? = null,
    ): CompileResult {
        val presetBlock = NecessityPresetTemplates.presetBlock(purposeId)
        val prompt = buildCompilePrompt(purposeId, presetBlock, corrections, existingPolicy)
        val raw = gemini.generateStrictJsonFromText(
            apiKey = apiKey,
            prompt = prompt,
            responseJsonSchema = NecessityCompileSchema.responseSchema(),
        )
        val text = GeminiResponseParser.extractResponseText(raw)
        val json = JSONObject(text)
        return CompileResult(
            userSummary = json.getString("userSummary").trim(),
            userRulesBlock = json.getString("userRulesBlock").trim(),
        )
    }

    fun buildPreview(
        purposeId: NecessityPurposeId,
        corrections: List<NecessityCorrection>,
        existingPolicy: CompiledNecessityPolicy?,
        apiKey: String,
    ): CompiledNecessityPolicy {
        val presetBlock = NecessityPresetTemplates.presetBlock(purposeId)
        val result = if (corrections.isEmpty()) {
            CompileResult(
                userSummary = existingPolicy?.userSummary?.takeIf { it.isNotBlank() }
                    ?: NecessityPresetTemplates.defaultSummary(purposeId),
                userRulesBlock = existingPolicy?.userRulesBlock.orEmpty(),
            )
        } else {
            compile(apiKey, purposeId, corrections, existingPolicy)
        }
        val userRules = result.userRulesBlock
        val promptBlock = NecessityPolicyMerger.mergePromptBlock(presetBlock, userRules)
        return CompiledNecessityPolicy(
            policyVersion = 0,
            purposeId = purposeId,
            compiledAt = Instant.now().toString(),
            userSummary = result.userSummary.ifBlank {
                NecessityPresetTemplates.defaultSummary(purposeId)
            },
            presetBlock = presetBlock,
            userRulesBlock = userRules,
            promptBlock = promptBlock,
            correctionsFingerprint = "",
        )
    }

    fun commitCompiledPolicy(
        store: NecessityPolicyStore,
        preview: CompiledNecessityPolicy,
    ): CompiledNecessityPolicy {
        val committed = preview.copy(
            policyVersion = store.nextPolicyVersion(),
            compiledAt = Instant.now().toString(),
            correctionsFingerprint = NecessityPolicyStore.fingerprint(preview.purposeId, emptyList()),
        )
        store.setPurposeId(preview.purposeId)
        store.saveCompiledPolicy(committed)
        store.clearCorrections()
        return committed
    }

    private fun buildCompilePrompt(
        purposeId: NecessityPurposeId,
        presetBlock: String,
        corrections: List<NecessityCorrection>,
        existingPolicy: CompiledNecessityPolicy?,
    ): String {
        val correctionsText = if (corrections.isEmpty()) {
            "（なし）"
        } else {
            corrections
                .groupBy { it.receiptId.orEmpty() }
                .entries
                .joinToString("\n\n") { (_, items) ->
                    val merchant = items.firstNotNullOfOrNull { it.merchantName?.takeIf { n -> n.isNotBlank() } }
                        ?: "（店名不明）"
                    val header = "### レシート: $merchant"
                    val lines = items.joinToString("\n") {
                        "- 「${it.phrase}」: AI ${it.scoreBefore} → 希望 ${it.scoreAfter}" +
                            (it.sourceItemName?.let { n -> "（商品: $n）" } ?: "")
                    }
                    "$header\n$lines"
                }
        }
        val existingPolicyText = if (existingPolicy != null) {
            """
            |## 現在のユーザー向け方針（コンパイル済み）
            |要約: ${existingPolicy.userSummary}
            |ルール:
            |${existingPolicy.userRulesBlock.ifBlank { "（なし）" }}
            |
            |上記を踏まえ、訂正例が示す **AI の判定がユーザー期待とずれた理由** を推論してください。
            |（例: スコアが高すぎた＝必須と誤認した用途、低すぎた＝生活必需品を軽視した、など）
            |その上で既存ルールを **改善・統合** した新しい方針を出力してください。単純な追記ではなく矛盾を解消してください。
            """.trimMargin()
        } else {
            """
            |## 現在のユーザー向け方針
            |（初回コンパイルのため、ユーザー固有ルールはまだありません）
            |
            |訂正例から、ユーザーが **なぜ AI の必須度を直したのか** を推論し、一般化したルールを作成してください。
            """.trimMargin()
        }
        return """
            |あなたは家計簿アプリの「必須度ポリシー」コンパイラです。
            |ユーザーの訂正例と既存方針をもとに、解析 AI が使う **客観的な判定ルール文** に一般化してください。
            |
            |## プリセット土台（変更しない・上書きしない）
            |目的: ${purposeId.label}
            |$presetBlock
            |
            |$existingPolicyText
            |
            |## 今回の訂正例（レシート／店名ごと、AI 判定 → ユーザーの希望）
            |店舗文脈（コンビニ・スーパー・外食など）も考慮し、なぜ AI のスコアが合わなかったかを推論してください。
            |$correctionsText
            |
            |## 出力要件
            |- userSummary: ユーザー向け **1行** の要約（40文字前後、読みやすい日本語）
            |- userRulesBlock: 解析 AI 向けの箇条書きルール（数値スコアは書かない。「〜は裁量寄り」「〜は必須寄り」など客観表現）
            |- 訂正例の意図（なぜ元のスコアが合わなかったか）をルールに反映すること
            |- プリセット土台と矛盾するルールは書かない
            |- 最大 1500 文字程度
        """.trimMargin()
    }
}
