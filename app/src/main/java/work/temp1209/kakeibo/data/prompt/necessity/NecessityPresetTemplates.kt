package work.temp1209.kakeibo.data.prompt.necessity

import work.temp1209.kakeibo.data.necessity.NecessityPurposeId

/**
 * 目的プリセットごとのプロンプト土台。
 * [boundaryCases] と [scoreBands] はプリセット別にチューニングする。
 */
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

    fun scoreBands(purposeId: NecessityPurposeId): String = when (purposeId) {
        NecessityPurposeId.BALANCE -> balanceScoreBands()
        NecessityPurposeId.SAVE -> saveScoreBands()
        NecessityPurposeId.MANAGE -> manageScoreBands()
        NecessityPurposeId.CONVENIENCE_CUT -> convenienceCutScoreBands()
        NecessityPurposeId.MINIMAL -> minimalScoreBands()
    }

    fun boundaryCases(purposeId: NecessityPurposeId): String = when (purposeId) {
        NecessityPurposeId.BALANCE -> balanceBoundaryCases()
        NecessityPurposeId.SAVE -> saveBoundaryCases()
        NecessityPurposeId.MANAGE -> manageBoundaryCases()
        NecessityPurposeId.CONVENIENCE_CUT -> convenienceCutBoundaryCases()
        NecessityPurposeId.MINIMAL -> minimalBoundaryCases()
    }

    private fun balanceScoreBands(): String = """
        |- 90〜100: 食料品（食材）・処方薬・生理用品・洗剤等の明確な生活必需品
        |- 70〜89: スーパー惣菜、コンビニ弁当・おにぎり、定期の通信費・光熱の実費、通勤交通
        |- 55〜69: 外食全般（ランチ・定食）、消耗する日用品（雑貨だが生活実用）、カフェの食事寄りメニュー
        |- 35〜54: スイーツ・お菓子・カフェ飲料単体、美容・サプリ（健康目的の裁量）、100均の装飾雑貨
        |- 15〜34: 趣味雑貨・コレクション、娯楽イベント、ゲーム買い切り（高額趣味寄りはさらに下げてよい）
        |- 0〜14: ゲーム課金・ガチャ、明確な贅沢品・高額趣味、非生活の衝動買い
    """.trimMargin()

    private fun saveScoreBands(): String = """
        |- 90〜100: 食材・処方薬・生理用品・洗剤等の **明確な生活必需品のみ**
        |- 65〜89: スーパー惣菜、コンビニ弁当・おにぎり、通信費・光熱の実費
        |- 45〜64: 外食・カフェの食事寄り、実用日用品（グレーゾーンは下限寄り）
        |- 25〜44: スイーツ・菓子・カフェ飲料単体、美容・サプリ、100均の装飾雑貨
        |- 10〜24: 趣味雑貨・娯楽、ゲーム買い切り、衝動買い寄りの小物
        |- 0〜9: ゲーム課金・ガチャ、明確な贅沢品
    """.trimMargin()

    private fun manageScoreBands(): String = balanceScoreBands()

    private fun convenienceCutScoreBands(): String = """
        |- 90〜100: 食材・処方薬・生理用品・洗剤等の明確な生活必需品
        |- 70〜89: スーパー惣菜、**コンビニ弁当・おにぎり・惣菜**、通信費・光熱
        |- 55〜69: 外食（コンビニ以外）、実用日用品、カフェの食事寄りメニュー
        |- 30〜54: **コンビニスイーツ・菓子・エナドリ**、カフェ飲料単体、美容・サプリ
        |- 15〜29: 趣味雑貨、ゲーム買い切り、100均の装飾小物
        |- 0〜14: ゲーム課金・ガチャ、明確な贅沢品
    """.trimMargin()

    private fun minimalScoreBands(): String = """
        |- 88〜100: 食材・処方薬・生理用品・洗剤等の **生活必需品に限る**
        |- 60〜87: 自炊用の調味料・米・パン、通勤の実費（外食・惣菜は原則含めない）
        |- 35〜59: どうしても必要な日用品（グレーゾーンは下限寄り）
        |- 15〜34: 外食・コンビニ食・カフェ、美容・サプリ
        |- 5〜14: スイーツ・菓子・趣味雑貨、ゲーム買い切り
        |- 0〜4: ゲーム課金・ガチャ、明確な贅沢品・衝動買い
    """.trimMargin()

    private fun balanceBoundaryCases(): String = boundaryTable(
        bento = "65〜80",
        sweets = "25〜45",
        hundredYenConsumable = "75〜90",
        hundredYenToy = "20〜40",
        hundredYenStorage = "55〜75",
        comms = "70〜88",
        gacha = "5〜20",
        ingredients = "88〜100",
        cafe = "35〜50",
    )

    private fun saveBoundaryCases(): String = boundaryTable(
        bento = "55〜70",
        sweets = "15〜35",
        hundredYenConsumable = "70〜85",
        hundredYenToy = "10〜30",
        hundredYenStorage = "45〜65",
        comms = "65〜85",
        gacha = "3〜15",
        ingredients = "90〜100",
        cafe = "25〜40",
    )

    private fun manageBoundaryCases(): String = balanceBoundaryCases()

    private fun convenienceCutBoundaryCases(): String = boundaryTable(
        bento = "65〜80",
        sweets = "15〜30",
        hundredYenConsumable = "75〜90",
        hundredYenToy = "15〜35",
        hundredYenStorage = "50〜70",
        comms = "70〜88",
        gacha = "5〜20",
        ingredients = "88〜100",
        cafe = "20〜35",
    )

    private fun minimalBoundaryCases(): String = boundaryTable(
        bento = "40〜55",
        sweets = "8〜22",
        hundredYenConsumable = "65〜80",
        hundredYenToy = "5〜18",
        hundredYenStorage = "30〜50",
        comms = "60〜82",
        gacha = "0〜10",
        ingredients = "92〜100",
        cafe = "15〜30",
    )

    private fun boundaryTable(
        bento: String,
        sweets: String,
        hundredYenConsumable: String,
        hundredYenToy: String,
        hundredYenStorage: String,
        comms: String,
        gacha: String,
        ingredients: String,
        cafe: String,
    ): String = """
        | 商品例 | 目安スコア | 理由 |
        |--------|-----------|------|
        | コンビニ弁当・おにぎり・惣菜 | $bento | 生活食・外食の実用 |
        | コンビニスイーツ・菓子・エナドリ（嗜好） | $sweets | 嗜好・甘味中心 |
        | 100均: テープ・電池・洗剤・ゴミ袋 | $hundredYenConsumable | 生活消耗品 |
        | 100均: 玩具・ステッカー・装飾小物 | $hundredYenToy | 趣味・衝動買い寄り |
        | 100均: 収納ボックス・ハンガー（実用） | $hundredYenStorage | 生活整理の実用品 |
        | 携帯・インターネット・通信系サブスク | $comms | 生活インフラ |
        | ゲーム課金・ガチャ | $gacha | 明確な裁量娯楽 |
        | スーパー食材（野菜・肉・米） | $ingredients | 食費の必需品 |
        | カフェコーヒー単体（食事なし） | $cafe | 嗜好飲料 |
    """.trimMargin()
}
