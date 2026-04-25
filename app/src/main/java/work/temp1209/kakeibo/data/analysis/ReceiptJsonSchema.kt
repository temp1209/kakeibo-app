package work.temp1209.kakeibo.data.analysis

import org.json.JSONArray
import org.json.JSONObject

object ReceiptJsonSchema {
    fun schemaV1(): JSONObject {
        val receipt = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("receiptDatetime", JSONObject().put("type", "string"))
                    .put("capturedAt", JSONObject().put("type", "string"))
                    .put("merchantName", JSONObject().put("type", "string"))
                    .put("totalAmountYen", JSONObject().put("type", "integer"))
                    .put("paymentMethod", JSONObject().put("type", "string"))
                    .put("paymentServiceName", JSONObject().put("type", "string")),
            )
            .put("required", JSONArray().put("receiptDatetime").put("merchantName").put("totalAmountYen"))

        val item = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("lineIndex", JSONObject().put("type", "integer"))
                    .put("itemName", JSONObject().put("type", "string"))
                    .put("quantity", JSONObject().put("type", "integer"))
                    .put("lineTotalYen", JSONObject().put("type", "integer"))
                    .put("categoryMajor", JSONObject().put("type", "string"))
                    .put("categoryMinor", JSONObject().put("type", "string"))
                    .put("necessityScore", JSONObject().put("type", "integer"))
                    .put("confidence", JSONObject().put("type", "number")),
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
            .put(
                "properties",
                JSONObject()
                    .put("schemaVersion", JSONObject().put("type", "string"))
                    .put("receipt", receipt)
                    .put("items", JSONObject().put("type", "array").put("items", item))
                    .put("warnings", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))),
            )
            .put("required", JSONArray().put("schemaVersion").put("receipt").put("items"))
    }
}

