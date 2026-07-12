package work.temp1209.kakeibo.data.prompt.necessity

import work.temp1209.kakeibo.data.necessity.CompiledNecessityPolicy
import work.temp1209.kakeibo.data.necessity.NecessityCorrection
import work.temp1209.kakeibo.data.necessity.NecessityPurposeId

/** ライン①: 訂正例 + 既存方針 → userSummary / userRulesBlock */
object NecessityCompilePrompt {

    fun build(
        purposeId: NecessityPurposeId,
        presetBlock: String,
        corrections: List<NecessityCorrection>,
        existingPolicy: CompiledNecessityPolicy?,
    ): String {
        val correctionsText = formatCorrections(corrections)
        val existingPolicyText = formatExistingPolicy(existingPolicy)
        return """
            |あなたは家計簿アプリの「必須度ポリシー」コンパイラです。
            |ユーザーの訂正例と既存方針をもとに、解析 AI が使う **客観的な判定ルール文** に一般化してください。
            |
            |## プリセット土台（変更しない・上書きしない）
            |目的: ${purposeId.label}
            |$presetBlock
            |
            |$existingPolicyText
            |
            |## 今回の訂正例（レシート／店名ごと、AI 判定 → ユーザーの希望）
            |店舗文脈（コンビニ・スーパー・外食など）も考慮し、なぜ AI のスコアが合わなかったかを推論してください。
            |訂正の **差分の大きさ**（例: 70→30 は大幅に裁量寄り、55→75 は必須寄りへ引き上げ）もルールの強さに反映すること。
            |$correctionsText
            |
            |## 出力要件
            |- userSummary: ユーザー向け **1行** の要約（40文字前後、読みやすい日本語）
            |- userRulesBlock: 解析 AI 向けの箇条書きルール（**個別の数値スコアは書かない**。「〜は裁量寄り」「〜は必須寄り」「グレーゾーンは低めを優先」など客観表現）
            |- 訂正例の意図（なぜ元のスコアが合わなかったか）と **調整の強さ**（微調整か帯シフトか）をルールに反映すること
            |- プリセット土台と矛盾するルールは書かない
            |- 最大 1500 文字程度
        """.trimMargin()
    }

    private fun formatCorrections(corrections: List<NecessityCorrection>): String {
        if (corrections.isEmpty()) return "（なし）"
        return corrections
            .groupBy { it.receiptId.orEmpty() }
            .entries
            .joinToString("\n\n") { (_, items) ->
                val merchant = items.firstNotNullOfOrNull { it.merchantName?.takeIf { n -> n.isNotBlank() } }
                    ?: "（店名不明）"
                val header = "### レシート: $merchant"
                val lines = items.joinToString("\n") {
                    val delta = it.scoreAfter - it.scoreBefore
                    val strength = when {
                        delta <= -20 -> "（大幅に下げ）"
                        delta <= -10 -> "（やや下げ）"
                        delta >= 20 -> "（大幅に上げ）"
                        delta >= 10 -> "（やや上げ）"
                        else -> "（微調整）"
                    }
                    "- 「${it.phrase}」: AI ${it.scoreBefore} → 希望 ${it.scoreAfter} $strength" +
                        (it.sourceItemName?.let { n -> "（商品: $n）" } ?: "")
                }
                "$header\n$lines"
            }
    }

    private fun formatExistingPolicy(existingPolicy: CompiledNecessityPolicy?): String =
        if (existingPolicy != null) {
            """
            |## 現在のユーザー向け方針（コンパイル済み）
            |要約: ${existingPolicy.userSummary}
            |ルール:
            |${existingPolicy.userRulesBlock.ifBlank { "（なし）" }}
            |
            |上記を踏まえ、訂正例が示す **AI の判定がユーザー期待とずれた理由** を推論してください。
            |（例: スコアが高すぎた＝必須と誤認した用途、低すぎた＝生活必需品を軽視した、など）
            |その上で既存ルールを **改善・統合** した新しい方針を出力してください。単純な追記ではなく矛盾を解消してください。
            """.trimMargin()
        } else {
            """
            |## 現在のユーザー向け方針
            |（初回コンパイルのため、ユーザー固有ルールはまだありません）
            |
            |訂正例から、ユーザーが **なぜ AI の必須度を直したのか** を推論し、一般化したルールを作成してください。
            """.trimMargin()
        }
}
