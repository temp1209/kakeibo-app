package work.temp1209.kakeibo.data.ai

import org.json.JSONObject
import work.temp1209.kakeibo.data.gemini.GeminiClient

class GeminiAiProvider(
    private val client: GeminiClient = GeminiClient(),
) : AiProvider {
    override val providerId: String = AiProviderId.GEMINI

    override fun testConnectivity(apiKey: String): String = client.testText(apiKey)

    override fun generateStrictJsonFromImage(
        apiKey: String,
        jpegBytes: ByteArray,
        prompt: String,
        responseJsonSchema: JSONObject,
    ): String = client.generateStrictJsonFromImage(
        apiKey = apiKey,
        jpegBytes = jpegBytes,
        prompt = prompt,
        responseJsonSchema = responseJsonSchema,
    )

    override fun generateStrictJsonFromText(
        apiKey: String,
        prompt: String,
        responseJsonSchema: JSONObject,
    ): String = client.generateStrictJsonFromText(
        apiKey = apiKey,
        prompt = prompt,
        responseJsonSchema = responseJsonSchema,
    )

    companion object {
        const val MODEL_NAME = "gemini-2.5-flash"
    }
}
