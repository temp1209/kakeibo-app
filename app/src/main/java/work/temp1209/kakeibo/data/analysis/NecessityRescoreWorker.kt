package work.temp1209.kakeibo.data.analysis

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONObject
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.gemini.GeminiClient
import work.temp1209.kakeibo.data.gemini.GeminiResponseParser
import work.temp1209.kakeibo.data.gemini.GeminiUserMessages
import work.temp1209.kakeibo.data.prompt.necessity.NecessityRescorePrompt
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
            return Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to GeminiApiKeyStore.MISSING_KEY_USER_MESSAGE),
            )
        }

        val store = NecessityPolicyStore(applicationContext)
        val policyBlock = store.getEffectivePromptBlock()
        val purposeId = store.getPurposeId()
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

        var updatedCount = 0
        try {
            for ((index, chunk) in chunks.withIndex()) {
                val prompt = NecessityRescorePrompt.build(chunk, policyBlock, purposeId)
                val raw = gemini.generateStrictJsonFromText(
                    apiKey = apiKey,
                    prompt = prompt,
                    responseJsonSchema = NecessityRescoreSchema.responseSchema(),
                )
                val scores = parseScores(GeminiResponseParser.extractResponseText(raw))
                val byId = scores.associateBy { it.itemId }
                val updated = chunk.map { item ->
                    val score = byId[item.itemId]?.necessityScore ?: item.necessityScore
                    item.copy(necessityScore = score.coerceIn(0, 100))
                }
                dao.upsertReceiptItems(updated)
                updatedCount += updated.size
                Log.d(TAG, "rescore chunk ${index + 1}/${chunks.size} updated=${updated.size}")
            }
        } catch (e: Exception) {
            val msg = GeminiUserMessages.necessityRescoreFailure(e, updatedCount)
            Log.w(TAG, "rescore failed updatedSoFar=$updatedCount", e)
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
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

    companion object {
        const val UNIQUE_WORK_NAME = "necessity_rescore_current_month"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val TAG = "NecessityRescoreWorker"
    }
}
