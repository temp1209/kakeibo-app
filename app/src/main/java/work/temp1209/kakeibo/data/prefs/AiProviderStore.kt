package work.temp1209.kakeibo.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import work.temp1209.kakeibo.data.ai.AiProviderConfig
import work.temp1209.kakeibo.data.ai.AiProviderId
import work.temp1209.kakeibo.data.ai.ProviderSlot
import java.util.UUID

/**
 * 複数 AI プロバイダ・スロットの端末内保存。
 * 既存の単一 Gemini キー（[LEGACY_KEY_API_KEY]）は初回読取時にスロットへマイグレーションする。
 */
class AiProviderStore(context: Context) {
    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasEnabledSlot(): Boolean {
        migrateLegacyIfNeeded()
        return getConfig().orderedEnabledSlots().any { readApiKey(it.slotId) != null }
    }

    fun getConfig(): AiProviderConfig {
        migrateLegacyIfNeeded()
        val slotsJson = prefs.getString(KEY_SLOTS_JSON, null)
        if (slotsJson.isNullOrBlank()) {
            return AiProviderConfig(slots = emptyList(), orderedSlotIds = emptyList())
        }
        val arr = JSONArray(slotsJson)
        val slots = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ProviderSlot(
                slotId = o.getString("slotId"),
                providerId = o.getString("providerId"),
                label = o.optString("label").ifBlank { "API" },
                enabled = o.optBoolean("enabled", true),
            )
        }
        val orderedRaw = prefs.getString(KEY_ORDERED_SLOT_IDS, null).orEmpty()
        val ordered = if (orderedRaw.isBlank()) {
            slots.map { it.slotId }
        } else {
            orderedRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
        return AiProviderConfig(slots = slots, orderedSlotIds = ordered)
    }

    fun readApiKey(slotId: String): String? =
        prefs.getString(keyForSlot(slotId), null)?.takeIf { it.isNotBlank() }

    fun addSlot(
        providerId: String,
        label: String,
        apiKey: String,
        enabled: Boolean = true,
    ): ProviderSlot {
        migrateLegacyIfNeeded()
        require(apiKey.isNotBlank()) { "apiKey is blank" }
        val slot = ProviderSlot(
            slotId = UUID.randomUUID().toString(),
            providerId = providerId,
            label = label.trim().ifBlank { defaultLabel(providerId) },
            enabled = enabled,
        )
        val config = getConfig()
        persist(
            slots = config.slots + slot,
            orderedSlotIds = config.orderedSlotIds + slot.slotId,
        )
        prefs.edit().putString(keyForSlot(slot.slotId), apiKey.trim()).apply()
        return slot
    }

    fun updateSlotMeta(
        slotId: String,
        label: String? = null,
        enabled: Boolean? = null,
    ) {
        migrateLegacyIfNeeded()
        val config = getConfig()
        val updated = config.slots.map { slot ->
            if (slot.slotId != slotId) slot
            else slot.copy(
                label = label?.trim()?.ifBlank { slot.label } ?: slot.label,
                enabled = enabled ?: slot.enabled,
            )
        }
        persist(updated, config.orderedSlotIds)
    }

    fun updateSlotApiKey(slotId: String, apiKey: String) {
        migrateLegacyIfNeeded()
        require(apiKey.isNotBlank()) { "apiKey is blank" }
        val config = getConfig()
        check(config.slots.any { it.slotId == slotId }) { "slot not found: $slotId" }
        prefs.edit().putString(keyForSlot(slotId), apiKey.trim()).apply()
    }

    fun removeSlot(slotId: String) {
        migrateLegacyIfNeeded()
        val config = getConfig()
        persist(
            slots = config.slots.filter { it.slotId != slotId },
            orderedSlotIds = config.orderedSlotIds.filter { it != slotId },
        )
        prefs.edit().remove(keyForSlot(slotId)).apply()
    }

    fun moveSlot(slotId: String, direction: Int) {
        migrateLegacyIfNeeded()
        require(direction == -1 || direction == 1)
        val config = getConfig()
        val order = config.orderedSlotIds.toMutableList()
        val idx = order.indexOf(slotId)
        if (idx < 0) return
        val target = idx + direction
        if (target !in order.indices) return
        val tmp = order[idx]
        order[idx] = order[target]
        order[target] = tmp
        persist(config.slots, order)
    }

    fun setOrderedSlotIds(orderedSlotIds: List<String>) {
        migrateLegacyIfNeeded()
        val config = getConfig()
        val known = config.slots.map { it.slotId }.toSet()
        val filtered = orderedSlotIds.filter { it in known }.distinct()
        val missing = config.slots.map { it.slotId }.filter { it !in filtered }
        persist(config.slots, filtered + missing)
    }

    /**
     * 後方互換: 単一キー保存。先頭スロットを更新、無ければ新規作成。
     */
    fun savePrimaryKey(apiKey: String, label: String = "メイン") {
        migrateLegacyIfNeeded()
        if (apiKey.isBlank()) return
        val config = getConfig()
        val primaryId = config.orderedSlotIds.firstOrNull()
        if (primaryId != null) {
            updateSlotApiKey(primaryId, apiKey)
            updateSlotMeta(primaryId, enabled = true)
        } else {
            addSlot(AiProviderId.GEMINI, label, apiKey, enabled = true)
        }
    }

    fun clearAllSlots() {
        migrateLegacyIfNeeded()
        val config = getConfig()
        val editor = prefs.edit()
        for (slot in config.slots) {
            editor.remove(keyForSlot(slot.slotId))
        }
        editor.remove(KEY_SLOTS_JSON)
        editor.remove(KEY_ORDERED_SLOT_IDS)
        editor.remove(LEGACY_KEY_API_KEY)
        editor.apply()
    }

    /**
     * バックアップ用メタ（キー本体は含めない）。
     */
    fun exportPublicMeta(): JSONObject {
        val config = getConfig()
        val slots = JSONArray()
        for (slot in config.orderedSlots()) {
            slots.put(
                JSONObject()
                    .put("slotId", slot.slotId)
                    .put("providerId", slot.providerId)
                    .put("label", slot.label)
                    .put("enabled", slot.enabled)
                    .put("hasKey", readApiKey(slot.slotId) != null),
            )
        }
        return JSONObject()
            .put("orderedSlotIds", JSONArray(config.orderedSlotIds))
            .put("slots", slots)
    }

    private fun persist(slots: List<ProviderSlot>, orderedSlotIds: List<String>) {
        val arr = JSONArray()
        for (slot in slots) {
            arr.put(
                JSONObject()
                    .put("slotId", slot.slotId)
                    .put("providerId", slot.providerId)
                    .put("label", slot.label)
                    .put("enabled", slot.enabled),
            )
        }
        prefs.edit()
            .putString(KEY_SLOTS_JSON, arr.toString())
            .putString(KEY_ORDERED_SLOT_IDS, orderedSlotIds.joinToString(","))
            .commit()
    }

    private fun migrateLegacyIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val legacy = prefs.getString(LEGACY_KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        val hasSlots = !prefs.getString(KEY_SLOTS_JSON, null).isNullOrBlank()
        if (legacy != null && !hasSlots) {
            val slotId = UUID.randomUUID().toString()
            val slot = ProviderSlot(
                slotId = slotId,
                providerId = AiProviderId.GEMINI,
                label = "メイン",
                enabled = true,
            )
            persist(listOf(slot), listOf(slotId))
            prefs.edit()
                .putString(keyForSlot(slotId), legacy)
                .remove(LEGACY_KEY_API_KEY)
                .putBoolean(KEY_MIGRATED, true)
                .apply()
        } else {
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        }
    }

    private fun keyForSlot(slotId: String): String = "$KEY_API_KEY_PREFIX$slotId"

    private fun defaultLabel(providerId: String): String =
        AiProviderId.displayName(providerId)

    companion object {
        const val MISSING_KEY_USER_MESSAGE =
            "AI APIキーが未設定です。設定画面で入力してください。"

        private const val FILE_NAME = "secrets"
        private const val LEGACY_KEY_API_KEY = "gemini_api_key"
        private const val KEY_SLOTS_JSON = "ai_provider_slots_json"
        private const val KEY_ORDERED_SLOT_IDS = "ai_provider_ordered_slot_ids"
        private const val KEY_API_KEY_PREFIX = "ai_api_key_"
        private const val KEY_MIGRATED = "ai_provider_migrated_v1"
    }
}
