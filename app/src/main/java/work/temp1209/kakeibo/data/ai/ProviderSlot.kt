package work.temp1209.kakeibo.data.ai

data class ProviderSlot(
    val slotId: String,
    val providerId: String,
    val label: String,
    val enabled: Boolean = true,
)

data class AiProviderConfig(
    val slots: List<ProviderSlot>,
    val orderedSlotIds: List<String>,
) {
    /**
     * 試行順序。orderedSlotIds に無いスロットも末尾に含め、ルータから消えないようにする。
     */
    fun orderedSlots(): List<ProviderSlot> {
        val byId = slots.associateBy { it.slotId }
        val ordered = orderedSlotIds.mapNotNull { byId[it] }
        val missing = slots.filter { it.slotId !in orderedSlotIds }
        return ordered + missing
    }

    fun orderedEnabledSlots(): List<ProviderSlot> =
        orderedSlots().filter { it.enabled }
}
