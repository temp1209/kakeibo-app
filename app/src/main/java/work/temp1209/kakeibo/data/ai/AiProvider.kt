package work.temp1209.kakeibo.data.ai

import org.json.JSONObject

/**
 * 厳格 JSON 生成の共通インターフェース。
 * プロバイダ差は実装側に閉じ込め、呼び出し側は同じ入出力契約を使う。
 */
interface AiProvider {
    val providerId: String

    fun testConnectivity(apiKey: String): String

    fun generateStrictJsonFromImage(
        apiKey: String,
        jpegBytes: ByteArray,
        prompt: String,
        responseJsonSchema: JSONObject,
    ): String

    fun generateStrictJsonFromText(
        apiKey: String,
        prompt: String,
        responseJsonSchema: JSONObject,
    ): String
}
