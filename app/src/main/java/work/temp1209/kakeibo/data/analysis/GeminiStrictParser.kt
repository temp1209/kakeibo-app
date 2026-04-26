package work.temp1209.kakeibo.data.analysis

import org.json.JSONArray
import org.json.JSONObject
import work.temp1209.kakeibo.data.analysis.model.GeminiReceiptResponse
import work.temp1209.kakeibo.data.analysis.model.ReceiptHeader
import work.temp1209.kakeibo.data.analysis.model.ReceiptItem
import work.temp1209.kakeibo.data.analysis.model.ReviewFlags

object GeminiStrictParser {
    fun parseStrictJson(strictJson: String): GeminiReceiptResponse {
        val root = JSONObject(strictJson)

        val schemaVersion = root.getString("schemaVersion")
        if (schemaVersion != "1.0") error("unsupported schemaVersion=$schemaVersion")

        val receiptObj = root.getJSONObject("receipt")
        val receipt = ReceiptHeader(
            receiptDatetime = receiptObj.getString("receiptDatetime"),
            capturedAt = receiptObj.optString("capturedAt").takeIf { it.isNotBlank() },
            merchantName = receiptObj.getString("merchantName"),
            totalAmountYen = receiptObj.getLong("totalAmountYen"),
            paymentMethod = receiptObj.optString("paymentMethod").takeIf { it.isNotBlank() },
            paymentServiceName = receiptObj.optString("paymentServiceName").takeIf { it.isNotBlank() },
        )

        val itemsArr = root.getJSONArray("items")
        val items = (0 until itemsArr.length()).map { i ->
            val it = itemsArr.getJSONObject(i)
            ReceiptItem(
                lineIndex = it.getInt("lineIndex"),
                itemName = it.getString("itemName"),
                quantity = it.getInt("quantity"),
                lineTotalYen = it.getLong("lineTotalYen"),
                categoryMajor = it.getString("categoryMajor"),
                categoryMinor = it.getString("categoryMinor"),
                necessityScore = it.getInt("necessityScore"),
                confidence = it.getDouble("confidence"),
            )
        }

        val warnings = root.optJSONArray("warnings")?.toStringList().orEmpty()

        return GeminiReceiptResponse(
            schemaVersion = schemaVersion,
            receipt = receipt,
            items = items,
            warnings = warnings,
        )
    }

    fun reviewFlags(response: GeminiReceiptResponse, confidenceThreshold: Double = 0.7): ReviewFlags {
        val reasons = mutableListOf<String>()

        // Required (Phase2): receiptDatetime/merchantName/totalAmountYen must exist by parsing contract.
        if (response.receipt.merchantName.isBlank()) reasons += "merchantName missing"
        if (response.receipt.receiptDatetime.isBlank()) reasons += "receiptDatetime missing"
        if (response.receipt.totalAmountYen < 0) reasons += "totalAmountYen invalid"

        val lowConfCount = response.items.count { it.confidence < confidenceThreshold }
        if (lowConfCount > 0) reasons += "low confidence items: $lowConfCount"

        val duplicateLineIndex = response.items
            .groupBy { it.lineIndex }
            .any { (idx, group) -> idx >= 0 && group.size >= 2 }
        if (duplicateLineIndex) reasons += "duplicate lineIndex"

        val invalidLineIndexCount = response.items.count { it.lineIndex < 0 }
        if (invalidLineIndexCount > 0) reasons += "invalid lineIndex: $invalidLineIndexCount"

        val needsReview = reasons.isNotEmpty()
        return ReviewFlags(needsReview = needsReview, reasons = reasons)
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { idx ->
            optString(idx).takeIf { it.isNotBlank() }
        }
}

