package work.temp1209.kakeibo.data.prefs

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import work.temp1209.kakeibo.data.necessity.CompiledNecessityPolicy
import work.temp1209.kakeibo.data.necessity.NecessityCorrection
import work.temp1209.kakeibo.data.necessity.NecessityCorrectionDirection
import work.temp1209.kakeibo.data.necessity.NecessityPolicyMerger
import work.temp1209.kakeibo.data.necessity.NecessityPresetTemplates
import work.temp1209.kakeibo.data.necessity.NecessityPurposeId
import java.time.Instant
import java.util.UUID

class NecessityPolicyStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getPurposeId(): NecessityPurposeId =
        NecessityPurposeId.fromStored(prefs.getString(KEY_PURPOSE_ID, null))

    fun setPurposeId(purposeId: NecessityPurposeId) {
        prefs.edit().putString(KEY_PURPOSE_ID, purposeId.name).apply()
    }

    fun listCorrections(): List<NecessityCorrection> {
        val json = prefs.getString(KEY_CORRECTIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<NecessityCorrection>>() {}.type
        return runCatching { gson.fromJson<List<NecessityCorrection>>(json, type) }.getOrDefault(emptyList())
    }

    fun saveCorrections(corrections: List<NecessityCorrection>) {
        prefs.edit().putString(KEY_CORRECTIONS, gson.toJson(corrections)).apply()
    }

    fun getCompiledPolicyOrNull(): CompiledNecessityPolicy? {
        val json = prefs.getString(KEY_COMPILED_POLICY, null) ?: return null
        return runCatching { gson.fromJson(json, CompiledNecessityPolicy::class.java) }.getOrNull()
    }

    fun saveCompiledPolicy(policy: CompiledNecessityPolicy) {
        prefs.edit()
            .putString(KEY_COMPILED_POLICY, gson.toJson(policy))
            .putInt(KEY_POLICY_VERSION, policy.policyVersion)
            .apply()
    }

    fun nextPolicyVersion(): Int {
        val next = prefs.getInt(KEY_POLICY_VERSION, 0) + 1
        prefs.edit().putInt(KEY_POLICY_VERSION, next).apply()
        return next
    }

    fun hasPendingCorrections(): Boolean {
        val compiled = getCompiledPolicyOrNull() ?: return listCorrections().isNotEmpty()
        return compiled.correctionsFingerprint != currentFingerprint()
    }

    fun currentFingerprint(): String = fingerprint(getPurposeId(), listCorrections())

    fun getEffectivePromptBlock(): String {
        val compiled = getCompiledPolicyOrNull()
        if (compiled != null && compiled.promptBlock.isNotBlank()) {
            return compiled.promptBlock
        }
        return NecessityPolicyMerger.fallbackPolicy(getPurposeId()).promptBlock
    }

    fun getEffectiveSummary(): String {
        val compiled = getCompiledPolicyOrNull()
        if (compiled != null && compiled.userSummary.isNotBlank()) {
            return compiled.userSummary
        }
        return NecessityPresetTemplates.defaultSummary(getPurposeId())
    }

    fun addCorrection(
        phrase: String,
        direction: NecessityCorrectionDirection,
        sourceItemName: String? = null,
    ): NecessityCorrection {
        val normalized = normalizePhrase(phrase)
        val current = listCorrections().toMutableList()
        current.removeAll { it.phrase == normalized && it.direction == direction }
        val correction = NecessityCorrection(
            correctionId = UUID.randomUUID().toString(),
            phrase = normalized,
            direction = direction,
            sourceItemName = sourceItemName?.trim()?.take(120),
            createdAt = Instant.now().toString(),
        )
        current.add(0, correction)
        val trimmed = current.take(MAX_CORRECTIONS)
        saveCorrections(trimmed)
        return correction
    }

    fun removeCorrection(correctionId: String) {
        val updated = listCorrections().filterNot { it.correctionId == correctionId }
        saveCorrections(updated)
    }

    fun importFromBackup(
        purposeId: NecessityPurposeId,
        corrections: List<NecessityCorrection>,
        compiledPolicy: CompiledNecessityPolicy?,
    ) {
        prefs.edit()
            .putString(KEY_PURPOSE_ID, purposeId.name)
            .putString(KEY_CORRECTIONS, gson.toJson(corrections.take(MAX_CORRECTIONS)))
            .apply()
        if (compiledPolicy != null) {
            saveCompiledPolicy(compiledPolicy)
        } else {
            prefs.edit().remove(KEY_COMPILED_POLICY).apply()
        }
    }

    fun exportSnapshot(): NecessityPolicyBackupSnapshot = NecessityPolicyBackupSnapshot(
        purposeId = getPurposeId(),
        corrections = listCorrections(),
        compiledPolicy = getCompiledPolicyOrNull(),
    )

    companion object {
        const val MAX_CORRECTIONS = 20
        const val MAX_ITEMS_PER_RESCORE_REQUEST = 50

        private const val PREFS_NAME = "necessity_policy_prefs"
        private const val KEY_PURPOSE_ID = "purpose_id"
        private const val KEY_CORRECTIONS = "corrections_json"
        private const val KEY_COMPILED_POLICY = "compiled_policy_json"
        private const val KEY_POLICY_VERSION = "policy_version"

        fun normalizePhrase(raw: String): String =
            raw.trim()
                .replace(Regex("\\s+"), " ")
                .take(40)

        fun fingerprint(purposeId: NecessityPurposeId, corrections: List<NecessityCorrection>): String {
            val body = corrections
                .sortedBy { it.correctionId }
                .joinToString("|") { "${it.phrase}:${it.direction.name}" }
            return "${purposeId.name}#$body".hashCode().toString()
        }
    }
}

data class NecessityPolicyBackupSnapshot(
    val purposeId: NecessityPurposeId,
    val corrections: List<NecessityCorrection>,
    val compiledPolicy: CompiledNecessityPolicy?,
)
