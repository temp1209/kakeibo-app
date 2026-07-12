package work.temp1209.kakeibo.data.necessity

import org.json.JSONObject
import work.temp1209.kakeibo.data.gemini.GeminiClient
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
    ): CompileResult {
        val presetBlock = NecessityPresetTemplates.presetBlock(purposeId)
        val prompt = buildCompilePrompt(purposeId, presetBlock, corrections)
        val raw = gemini.generateStrictJsonFromText(
            apiKey = apiKey,
            prompt = prompt,
            responseJsonSchema = NecessityCompileSchema.responseSchema(),
        )
        val text = extractResponseText(raw)
        val json = JSONObject(text)
        return CompileResult(
            userSummary = json.getString("userSummary").trim(),
            userRulesBlock = json.getString("userRulesBlock").trim(),
        )
    }

    fun compileAndSave(
        store: NecessityPolicyStore,
        apiKey: String,
        purposeId: NecessityPurposeId,
        corrections: List<NecessityCorrection>,
    ): CompiledNecessityPolicy {
        val presetBlock = NecessityPresetTemplates.presetBlock(purposeId)
        val result = if (corrections.isEmpty()) {
            CompileResult(
                userSummary = NecessityPresetTemplates.defaultSummary(purposeId),
                userRulesBlock = "",
            )
        } else {
            compile(apiKey, purposeId, corrections)
        }
        val userRules = result.userRulesBlock
        val promptBlock = NecessityPolicyMerger.mergePromptBlock(presetBlock, userRules)
        val fingerprint = NecessityPolicyStore.fingerprint(purposeId, corrections)
        val policy = CompiledNecessityPolicy(
            policyVersion = store.nextPolicyVersion(),
            purposeId = purposeId,
            compiledAt = Instant.now().toString(),
            userSummary = result.userSummary.ifBlank {
                NecessityPresetTemplates.defaultSummary(purposeId)
            },
            presetBlock = presetBlock,
            userRulesBlock = userRules,
            promptBlock = promptBlock,
            correctionsFingerprint = fingerprint,
        )
        store.saveCompiledPolicy(policy)
        return policy
    }

    private fun buildCompilePrompt(
        purposeId: NecessityPurposeId,
        presetBlock: String,
        corrections: List<NecessityCorrection>,
    ): String {
        val correctionsText = if (corrections.isEmpty()) {
            "（なし）"
        } else {
            corrections.joinToString("\n") {
                "- 「${it.phrase}」→ ${it.direction.toPromptLabel()}${it.sourceItemName?.let { n -> "（元: $n）" } ?: ""}"
            }
        }
        return """
            |あなたは家計簿アプリの「必須度ポリシー」コンパイラです。
            |ユーザーの訂正例を、解析 AI が使う **客観的な判定ルール文** に一般化してください。
            |
            |## プリセット土台（変更しない・上書きしない）
            |目的: ${purposeId.label}
            |$presetBlock
            |
            |## ユーザー訂正例
            |$correctionsText
            |
            |## 出力要件
            |- userSummary: ユーザー向け **1行** の要約（40文字前後、読みやすい日本語）
            |- userRulesBlock: 解析 AI 向けの箇条書きルール（数値スコアは書かない。「〜は裁量寄り」「〜は必須寄り」など客観表現）
            |- 訂正例がない場合は userRulesBlock を空文字にしてよい
            |- プリセット土台と矛盾するルールは書かない
            |- 最大 1500 文字程度
        """.trimMargin()
    }

    private fun extractResponseText(rawResponse: String): String {
        val root = JSONObject(rawResponse)
        val candidates = root.getJSONArray("candidates")
        if (candidates.length() == 0) error("no candidates")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        if (parts.length() == 0) error("no parts")
        val text = parts.getJSONObject(0).optString("text")
        if (text.isBlank()) error("empty response text")
        return text.trim()
    }
}
