package work.temp1209.kakeibo.data.necessity

object NecessityPolicyMerger {

    fun mergePromptBlock(presetBlock: String, userRulesBlock: String): String {
        val preset = presetBlock.trim()
        val user = userRulesBlock.trim()
        return buildString {
            appendLine("## ユーザー向け必須度方針（プリセット）")
            appendLine(preset)
            if (user.isNotEmpty()) {
                appendLine()
                appendLine("## ユーザー固有の追加ルール")
                appendLine(user)
            }
            appendLine()
            appendLine("上記はベースのスコア帯を上書きする追加方針である。商品の用途を最優先し、±10点程度の調整でよい。")
        }.trim()
    }

    fun fallbackPolicy(purposeId: NecessityPurposeId): CompiledNecessityPolicy {
        val preset = NecessityPresetTemplates.presetBlock(purposeId)
        val promptBlock = mergePromptBlock(preset, userRulesBlock = "")
        return CompiledNecessityPolicy(
            policyVersion = 0,
            purposeId = purposeId,
            compiledAt = "",
            userSummary = NecessityPresetTemplates.defaultSummary(purposeId),
            presetBlock = preset,
            userRulesBlock = "",
            promptBlock = promptBlock,
            correctionsFingerprint = "",
        )
    }
}
