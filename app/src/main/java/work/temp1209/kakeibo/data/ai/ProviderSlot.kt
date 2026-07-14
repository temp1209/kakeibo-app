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
    fun orderedEnabledSlots(): List<ProviderSlot> {
        val byId = slots.associateBy { it.slotId }
        return orderedSlotIds.mapNotNull { byId[it] }.filter { it.enabled }
    }

    fun orderedSlots(): List<ProviderSlot> {
        val byId = slots.associateBy { it.slotId }
        val ordered = orderedSlotIds.mapNotNull { byId[it] }
        val missing = slots.filter { it.slotId !in orderedSlotIds }
        return ordered + missing
    }
}
