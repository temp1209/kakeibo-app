package work.temp1209.kakeibo.data.necessity

import org.json.JSONObject

object NecessityRescoreSchema {
    fun responseSchema(): JSONObject = JSONObject(
        """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["items"],
          "properties": {
            "items": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["itemId", "necessityScore"],
                "properties": {
                  "itemId": { "type": "string" },
                  "necessityScore": { "type": "integer", "minimum": 0, "maximum": 100 }
                }
              }
            }
          }
        }
        """.trimIndent(),
    )
}
