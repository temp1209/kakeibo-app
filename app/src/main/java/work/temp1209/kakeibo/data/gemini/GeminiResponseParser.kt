package work.temp1209.kakeibo.data.gemini

import org.json.JSONObject

object GeminiResponseParser {

    fun extractResponseText(rawResponse: String): String {
        val root = JSONObject(rawResponse)
        val candidates = root.getJSONArray("candidates")
        if (candidates.length() == 0) error("no candidates")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        if (parts.length() == 0) error("no parts")
        val text = parts.getJSONObject(0).optString("text")
        if (text.isBlank()) error("empty response text")
        return text.trim()
    }
}
