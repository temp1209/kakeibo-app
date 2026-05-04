package work.temp1209.kakeibo.data.analysis

import org.json.JSONArray
import org.json.JSONObject

object ReceiptJsonSchema {
    fun schemaV1(): JSONObject {
        val paymentMethodEnum = JSONArray()
            .put("CASH")
            .put("CREDIT_CARD")
            .put("DEBIT_CARD")
            .put("TRANSPORT_IC")
            .put("E_MONEY")
            .put("QR_PAYMENT")
            .put("BANK_TRANSFER_OR_DEBIT")
            .put("UNKNOWN")

        val categoryMajorEnum = JSONArray()
            .put("FOOD")
            .put("DAILY_GOODS")
            .put("TRANSPORT")
            .put("HOUSING")
            .put("UTILITIES")
            .put("COMMUNICATION")
            .put("MEDICAL")
            .put("EDUCATION")
            .put("SOCIAL")
            .put("ENTERTAINMENT")
            .put("CLOTHING")
            .put("OTHER")

        val categoryMinorEnum = JSONArray()
            // FOOD
            .put("スーパー").put("外食").put("その他")
            // DAILY_GOODS
            .put("衛生用品").put("雑貨（小物）").put("家具家電")
            // TRANSPORT
            .put("電車/バス").put("定期券")
            // HOUSING
            .put("家賃/住宅ローン").put("家具（大物）")
            // UTILITIES
            .put("電気").put("ガス").put("水道")
            // COMMUNICATION
            .put("携帯").put("インターネット").put("サブスク（通信系）")
            // MEDICAL
            .put("病院").put("薬").put("健康/検診")
            // EDUCATION
            .put("教材")
            // SOCIAL
            .put("交際費").put("飲み会").put("贈り物").put("冠婚葬祭")
            // ENTERTAINMENT
            .put("趣味").put("旅行").put("イベント").put("ゲーム（買い切り）").put("ゲーム（課金）")
            // CLOTHING
            .put("衣類").put("靴/バッグ").put("クリーニング")
            // OTHER
            .put("返済")
            // Common "その他" for each major (same label)
            .put("その他")

        val receipt = JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put(
                "properties",
                JSONObject()
                    .put(
                        "receiptDatetime",
                        JSONObject()
                            .put("type", "string")
                            .put(
                                "description",
                                "ISO 8601、日本時刻想定で末尾+09:00（例 2024-05-01T14:30:00+09:00）。時刻不明なら同日T00:00:00+09:00。",
                            ),
                    )
                    .put("capturedAt", JSONObject().put("type", "string"))
                    .put("merchantName", JSONObject().put("type", "string"))
                    .put("totalAmountYen", JSONObject().put("type", "integer").put("minimum", 0))
                    .put("paymentMethod", JSONObject().put("type", "string").put("enum", paymentMethodEnum))
                    .put("paymentServiceName", JSONObject().put("type", "string")),
            )
            .put("required", JSONArray().put("receiptDatetime").put("merchantName").put("totalAmountYen"))

        val item = JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put(
                "properties",
                JSONObject()
                    .put(
                        "lineIndex",
                        JSONObject()
                            .put("type", "integer")
                            .put("minimum", 0)
                            .put(
                                "description",
                                "itemsに出力する商品行を上から0起算の連番。セット統合後もレシート内で一意。",
                            ),
                    )
                    .put(
                        "itemName",
                        JSONObject()
                            .put("type", "string")
                            .put(
                                "description",
                                "送信プロンプトの「1明細=1商品」「セット統合」「型番からの日本語名」に従う。",
                            ),
                    )
                    .put("quantity", JSONObject().put("type", "integer").put("minimum", 1))
                    .put("lineTotalYen", JSONObject().put("type", "integer").put("minimum", 0))
                    .put("categoryMajor", JSONObject().put("type", "string").put("enum", categoryMajorEnum))
                    .put("categoryMinor", JSONObject().put("type", "string").put("enum", categoryMinorEnum))
                    .put("necessityScore", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100))
                    .put("confidence", JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1)),
            )
            .put(
                "required",
                JSONArray()
                    .put("lineIndex")
                    .put("itemName")
                    .put("quantity")
                    .put("lineTotalYen")
                    .put("categoryMajor")
                    .put("categoryMinor")
                    .put("necessityScore")
                    .put("confidence"),
            )

        return JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put(
                "properties",
                JSONObject()
                    .put("schemaVersion", JSONObject().put("type", "string").put("enum", JSONArray().put("1.0")))
                    .put("receipt", receipt)
                    .put("items", JSONObject().put("type", "array").put("items", item))
                    .put("warnings", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))),
            )
            .put("required", JSONArray().put("schemaVersion").put("receipt").put("items"))
    }
}

