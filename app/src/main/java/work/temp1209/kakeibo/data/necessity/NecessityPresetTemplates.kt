package work.temp1209.kakeibo.data.necessity

object NecessityPresetTemplates {

    fun presetBlock(purposeId: NecessityPurposeId): String = when (purposeId) {
        NecessityPurposeId.BALANCE -> """
            |全体方針: 一般家庭の家計における生活維持と裁量のバランスを保つ。
            |嗜好品と必需品の相対差を明確にし、店舗種別だけで一括判定しない。
        """.trimMargin()

        NecessityPurposeId.SAVE -> """
            |全体方針: 無駄遣い（裁量支出）の可視化を優先する。
            |生活必需品以外はやや裁量寄りに判定する傾向を強める。
            |スイーツ・嗜好飲料・衝動買い・趣味雑貨は積極的に低めの帯を選ぶ。
        """.trimMargin()

        NecessityPurposeId.MANAGE -> """
            |全体方針: 家計把握のため一貫した分類を優先する。
            |境界付近（45〜55）は商品用途で明確に振り分け、曖昧な場合は50付近に寄せる。
        """.trimMargin()

        NecessityPurposeId.CONVENIENCE_CUT -> """
            |全体方針: コンビニの嗜好品・カフェ飲料単体・衝動買いスナックを裁量寄りに判定する。
            |コンビニ弁当・おにぎり・惣菜など生活食は必須寄りの帯を維持する。
        """.trimMargin()

        NecessityPurposeId.MINIMAL -> """
            |全体方針: 明確な生活必需品のみ高スコアとする。
            |外食・嗜好品・美容・趣味は裁量寄りに判定し、グレーゾーンは低めを優先する。
        """.trimMargin()
    }

    fun defaultSummary(purposeId: NecessityPurposeId): String = when (purposeId) {
        NecessityPurposeId.BALANCE -> "バランス型の標準的な必須／裁量判定です。"
        NecessityPurposeId.SAVE -> "嗜好品・衝動買いを裁量寄りに判定します。"
        NecessityPurposeId.MANAGE -> "一貫した分類で家計を把握しやすくします。"
        NecessityPurposeId.CONVENIENCE_CUT -> "コンビニ嗜好品・カフェ単体を裁量寄りに判定します。"
        NecessityPurposeId.MINIMAL -> "生活必需品のみを高スコアとする厳しめの判定です。"
    }
}
