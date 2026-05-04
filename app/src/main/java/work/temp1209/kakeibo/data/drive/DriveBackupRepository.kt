package work.temp1209.kakeibo.data.drive

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriveFileMeta(
    val id: String,
    val name: String,
    val modifiedTimeMs: Long,
)

object DriveBackupRepository {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    private const val API_BASE = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"

    @Suppress("DEPRECATION")
    suspend fun getAccessToken(context: Context, account: GoogleSignInAccount): String = withContext(Dispatchers.IO) {
        val acc = account.account ?: error("account missing")
        GoogleAuthUtil.getToken(
            context.applicationContext,
            acc,
            "oauth2:${GoogleSignInHelper.DRIVE_APPDATA_SCOPE}",
        )
    }

    private fun filesListUrl(pageToken: String?): String {
        val b = "$API_BASE/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,modifiedTime)")
            .addQueryParameter("pageSize", "200")
        if (!pageToken.isNullOrEmpty()) {
            b.addQueryParameter("pageToken", pageToken)
        }
        return b.build().toString()
    }

    suspend fun listJsonFiles(token: String): List<DriveFileMeta> = withContext(Dispatchers.IO) {
        buildList {
            var pageToken: String? = null
            do {
                val req = Request.Builder()
                    .url(filesListUrl(pageToken))
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw DriveHttpException(resp.code, "Drive list failed: ${resp.code} $body")
                    }
                    val root = JSONObject(body)
                    pageToken = root.optString("nextPageToken", "").takeIf { it.isNotEmpty() }
                    val arr = root.optJSONArray("files") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.optString("id", "")
                        val name = o.optString("name", "")
                        if (id.isBlank()) continue
                        val mt = o.optString("modifiedTime", "")
                        val ms = runCatching { java.time.Instant.parse(mt).toEpochMilli() }.getOrDefault(0L)
                        add(DriveFileMeta(id = id, name = name, modifiedTimeMs = ms))
                    }
                }
            } while (!pageToken.isNullOrEmpty())
        }
    }

    suspend fun findByName(token: String, name: String): DriveFileMeta? =
        listJsonFiles(token).firstOrNull { it.name == name }

    suspend fun uploadOrReplaceJson(token: String, fileName: String, json: String): Unit = withContext(Dispatchers.IO) {
        val existing = findByName(token, fileName)
        val media = json.toRequestBody("application/json; charset=UTF-8".toMediaType())
        if (existing != null) {
            val url = "$UPLOAD_BASE/files/${existing.id}?uploadType=media"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .put(media)
                .build()
            http.newCall(req).execute().use { resp ->
                val errBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw DriveHttpException(resp.code, "Drive upload failed: ${resp.code} $errBody")
                }
            }
        } else {
            val metaJson = JSONObject()
                .put("name", fileName)
                .put("mimeType", "application/json")
                .toString()
            val createReq = Request.Builder()
                .url("$API_BASE/files")
                .header("Authorization", "Bearer $token")
                .post(metaJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
            val id = http.newCall(createReq).execute().use { resp ->
                val b = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw DriveHttpException(resp.code, "Drive create failed: ${resp.code} $b")
                }
                JSONObject(b).getString("id")
            }
            val putUrl = "$UPLOAD_BASE/files/$id?uploadType=media"
            val putReq = Request.Builder()
                .url(putUrl)
                .header("Authorization", "Bearer $token")
                .put(media)
                .build()
            try {
                http.newCall(putReq).execute().use { resp ->
                    val errBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw DriveHttpException(resp.code, "Drive media upload failed: ${resp.code} $errBody")
                    }
                }
            } catch (e: Exception) {
                runCatching { deleteById(token, id) }
                throw e
            }
        }
    }

    suspend fun downloadUtf8(token: String, fileId: String): String? = withContext(Dispatchers.IO) {
        val url = "$API_BASE/files/$fileId?alt=media"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        }
    }

    suspend fun deleteById(token: String, fileId: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API_BASE/files/$fileId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        http.newCall(req).execute().use { }
    }
}
