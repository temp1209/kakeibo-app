package work.temp1209.kakeibo.data.necessity

data class CompiledNecessityPolicy(
    val policyVersion: Int,
    val purposeId: NecessityPurposeId,
    val compiledAt: String,
    val userSummary: String,
    val presetBlock: String,
    val userRulesBlock: String,
    val promptBlock: String,
    val correctionsFingerprint: String,
)
