package work.temp1209.kakeibo.data.necessity

import org.json.JSONArray
import org.json.JSONObject
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.prefs.NecessityPolicyStore

object NecessityRescorePrompt {

    fun build(items: List<ReceiptItemEntity>, userPolicyBlock: String): String {
        val itemsJson = JSONArray()
        for (item in items) {
            itemsJson.put(
                JSONObject()
                    .put("itemId", item.itemId)
                    .put("itemName", item.itemName)
                    .put("lineTotalYen", item.lineTotalYen)
                    .put("categoryMajor", item.categoryMajor)
                    .put("categoryMinor", item.categoryMinor)
                    .put("necessityScore", item.necessityScore),
            )
        }
        return """
            |あなたは家計簿アプリの必須度（necessityScore）再評価エンジンです。
            |入力の明細リストに対し、ユーザー方針に従って necessityScore（0〜100）だけを付け直してください。
            |
            |## ルール
            |- 出力は厳格 JSON のみ（スキーマに従う）
            |- itemId は入力と完全一致させる。入力にない itemId を追加しない
            |- 二値化境界は 50（≥50 必須寄り、<50 裁量寄り）
            |- 商品の用途を最優先。店舗種別だけで一括判定しない
            |- 1リクエストあたり最大 ${NecessityPolicyStore.MAX_ITEMS_PER_RESCORE_REQUEST} 件
            |
            |## ユーザー向け必須度方針
            |$userPolicyBlock
            |
            |## 入力明細（JSON）
            |$itemsJson
        """.trimMargin()
    }
}
