package work.temp1209.kakeibo.data.gemini

import android.util.Log
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration

class GeminiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .writeTimeout(Duration.ofSeconds(60))
        .readTimeout(Duration.ofSeconds(120))
        .callTimeout(Duration.ofSeconds(180))
        .build(),
) {
    fun testText(apiKey: String): String {
        Log.d(TAG, "testText start (keyPresent=${apiKey.isNotBlank()})")
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "ping")),
                    ),
                ),
            )
            .toString()

        val request = Request.Builder()
            .url("$BASE_URL/models/$MODEL:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON.toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val snippet = raw.take(300)
                Log.w(TAG, "testText failed http=${resp.code} bodySnippet=${snippet}")
                throw IllegalStateException("HTTP ${resp.code}: $snippet")
            }
            Log.d(TAG, "testText ok http=${resp.code} bodySize=${raw.length}")
            return raw
        }
    }

    fun generateStrictJsonFromImage(
        apiKey: String,
        jpegBytes: ByteArray,
        prompt: String,
        responseJsonSchema: JSONObject,
    ): String {
        Log.d(TAG, "generateStrictJsonFromImage start bytes=${jpegBytes.size} promptChars=${prompt.length}")
        val imgB64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", imgB64),
                ),
            )

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", parts),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseJsonSchema", responseJsonSchema),
            )
            .toString()

        val request = Request.Builder()
            .url("$BASE_URL/models/$MODEL:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON.toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val snippet = raw.take(800)
                Log.w(TAG, "generateContent failed http=${resp.code} bodySnippet=${snippet}")
                throw IllegalStateException("HTTP ${resp.code}: $snippet")
            }
            Log.d(TAG, "generateContent ok http=${resp.code} bodySize=${raw.length}")
            return raw
        }
    }

    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODEL = "gemini-2.5-flash"
        private const val JSON = "application/json; charset=utf-8"
    }
}

