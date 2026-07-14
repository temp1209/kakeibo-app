package work.temp1209.kakeibo.data.prefs

import android.content.Context

/**
 * 後方互換ファサード。実体は [AiProviderStore]（複数スロット）。
 *
 * - [saveKey]: オンボーディング用。複数スロット時は「メイン」ラベルを優先更新。
 * - [deleteKey]: 全スロット削除（旧「APIキー削除」相当。設定 UI からは未使用）。
 */
class GeminiApiKeyStore(context: Context) {
    private val providerStore = AiProviderStore(context)

    fun hasKey(): Boolean = providerStore.hasEnabledSlot()

    fun saveKey(apiKey: String) {
        providerStore.savePrimaryKey(apiKey)
    }

    fun deleteKey() {
        providerStore.clearAllSlots()
    }

    fun readKeyOrNull(): String? {
        val slot = providerStore.getConfig().orderedEnabledSlots().firstOrNull() ?: return null
        return providerStore.readApiKey(slot.slotId)
    }

    companion object {
        const val MISSING_KEY_USER_MESSAGE = AiProviderStore.MISSING_KEY_USER_MESSAGE
    }
}
