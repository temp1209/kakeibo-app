package work.temp1209.kakeibo.data.analysis

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.necessity.NecessityRescorePrompt
import work.temp1209.kakeibo.data.necessity.NecessityRescoreSchema
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore
import work.temp1209.kakeibo.data.prefs.NecessityPolicyStore
import java.time.YearMonth
import java.time.ZoneId

class NecessityRescoreWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val apiKey = GeminiApiKeyStore(applicationContext).readKeyOrNull()
        if (apiKey == null) {
            Log.w(TAG, "api key missing")
            return Result.failure()
        }

        val store = NecessityPolicyStore(applicationContext)
        val policyBlock = store.getEffectivePromptBlock()
        val yearMonth = YearMonth.now(ZoneId.systemDefault()).toString()
        val dao = AppDatabase.get(applicationContext).receiptDao()
        val allItems = dao.listNonAdjustmentItemsInMonth(yearMonth)
        if (allItems.isEmpty()) {
            Log.d(TAG, "no items for month=$yearMonth")
            return Result.success()
        }

        val gemini = GeminiClient()
        val chunkSize = NecessityPolicyStore.MAX_ITEMS_PER_RESCORE_REQUEST
        val chunks = allItems.chunked(chunkSize)
        Log.d(TAG, "rescore start month=$yearMonth items=${allItems.size} chunks=${chunks.size}")

        try {
            for ((index, chunk) in chunks.withIndex()) {
                val prompt = NecessityRescorePrompt.build(chunk, policyBlock)
                val raw = gemini.generateStrictJsonFromText(
                    apiKey = apiKey,
                    prompt = prompt,
                    responseJsonSchema = NecessityRescoreSchema.responseSchema(),
                )
                val scores = parseScores(extractResponseText(raw))
                val byId = scores.associateBy { it.itemId }
                val updated = chunk.map { item ->
                    val score = byId[item.itemId]?.necessityScore ?: item.necessityScore
                    item.copy(necessityScore = score.coerceIn(0, 100))
                }
                dao.upsertReceiptItems(updated)
                Log.d(TAG, "rescore chunk ${index + 1}/${chunks.size} updated=${updated.size}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "rescore failed", e)
            return Result.failure()
        }

        return Result.success()
    }

    private data class ScoreRow(val itemId: String, val necessityScore: Int)

    private fun parseScores(text: String): List<ScoreRow> {
        val root = JSONObject(text)
        val items = root.getJSONArray("items")
        val out = ArrayList<ScoreRow>(items.length())
        for (i in 0 until items.length()) {
            val row = items.getJSONObject(i)
            out += ScoreRow(
                itemId = row.getString("itemId"),
                necessityScore = row.getInt("necessityScore"),
            )
        }
        return out
    }

    private fun extractResponseText(rawResponse: String): String {
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

    companion object {
        const val UNIQUE_WORK_NAME = "necessity_rescore_current_month"
        private const val TAG = "NecessityRescoreWorker"
    }
}
