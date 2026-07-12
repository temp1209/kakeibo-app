package work.temp1209.kakeibo.data.necessity

enum class NecessityPurposeId(val label: String) {
    BALANCE("バランス（デフォルト）"),
    SAVE("節約・無駄遣いを減らす"),
    MANAGE("家計を把握する"),
    CONVENIENCE_CUT("コンビニ・外食を減らしたい"),
    MINIMAL("必要最低限だけ必須"),
    ;

    companion object {
        fun fromStored(value: String?): NecessityPurposeId =
            entries.find { it.name == value } ?: BALANCE
    }
}
