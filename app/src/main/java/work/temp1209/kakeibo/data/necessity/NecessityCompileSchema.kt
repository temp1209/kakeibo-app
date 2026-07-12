package work.temp1209.kakeibo.data.necessity

import org.json.JSONObject

object NecessityCompileSchema {
    fun responseSchema(): JSONObject = JSONObject(
        """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["userSummary", "userRulesBlock"],
          "properties": {
            "userSummary": { "type": "string" },
            "userRulesBlock": { "type": "string" }
          }
        }
        """.trimIndent(),
    )
}
