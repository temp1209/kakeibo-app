package work.temp1209.kakeibo.data.necessity

import org.json.JSONObject
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.gemini.GeminiResponseParser
import work.temp1209.kakeibo.data.prefs.NecessityPolicyStore
import work.temp1209.kakeibo.data.prompt.necessity.NecessityCompilePrompt
import work.temp1209.kakeibo.data.prompt.necessity.NecessityPolicyPromptMerger
import work.temp1209.kakeibo.data.prompt.necessity.NecessityPresetTemplates
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
        val prompt = NecessityCompilePrompt.build(purposeId, presetBlock, corrections, existingPolicy)
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
        val promptBlock = NecessityPolicyPromptMerger.mergePromptBlock(presetBlock, userRules, purposeId)
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
}
