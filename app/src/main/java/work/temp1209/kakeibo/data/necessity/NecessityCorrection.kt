package work.temp1209.kakeibo.data.necessity

data class NecessityCorrection(
    val correctionId: String,
    val phrase: String,
    val direction: NecessityCorrectionDirection,
    val sourceItemName: String? = null,
    val createdAt: String,
)

enum class NecessityCorrectionDirection {
    ESSENTIAL,
    DISCRETIONARY,
    ;

    fun toPromptLabel(): String = when (this) {
        ESSENTIAL -> "必須寄り"
        DISCRETIONARY -> "裁量寄り"
    }
}
