package work.temp1209.kakeibo.data.prompt.necessity

import work.temp1209.kakeibo.data.necessity.CompiledNecessityPolicy
import work.temp1209.kakeibo.data.necessity.NecessityPurposeId

object NecessityPolicyPromptMerger {

    fun mergePromptBlock(presetBlock: String, userRulesBlock: String, purposeId: NecessityPurposeId): String {
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
            appendLine(adjustmentNote(purposeId))
        }.trim()
    }

    private fun adjustmentNote(purposeId: NecessityPurposeId): String = when (purposeId) {
        NecessityPurposeId.BALANCE, NecessityPurposeId.MANAGE ->
            "上記はベースのスコア帯を上書きする追加方針である。商品の用途を最優先し、±10点程度の調整でよい。"

        NecessityPurposeId.SAVE, NecessityPurposeId.MINIMAL ->
            "上記はベースのスコア帯を上書きする追加方針である。商品の用途を最優先し、訂正例・ユーザー固有ルールに基づき **15〜25点程度の帯シフト** も許容する（嗜好品・グレーゾーンは積極的に低め）。"

        NecessityPurposeId.CONVENIENCE_CUT ->
            "上記はベースのスコア帯を上書きする追加方針である。コンビニ嗜好品・カフェ飲料単体などは **10〜20点程度下げる** 調整を優先する。生活食（弁当・おにぎり等）は高めを維持する。"
    }

    fun fallbackPolicy(purposeId: NecessityPurposeId): CompiledNecessityPolicy {
        val preset = NecessityPresetTemplates.presetBlock(purposeId)
        val promptBlock = mergePromptBlock(preset, userRulesBlock = "", purposeId)
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
