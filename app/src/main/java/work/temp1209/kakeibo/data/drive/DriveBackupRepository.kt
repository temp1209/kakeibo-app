package work.temp1209.kakeibo.data.drive

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriveFileMeta(
    val id: String,
    val name: String,
    val modifiedTimeMs: Long,
)

object DriveBackupRepository {

    const val MANIFEST_FILE_NAME = "manifest.json"
    private const val SNAPSHOT_PREFIX = "snap-"

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

    suspend fun readManifest(token: String): BackupManifest? = withContext(Dispatchers.IO) {
        val meta = findByExactName(token, MANIFEST_FILE_NAME) ?: return@withContext null
        val body = downloadUtf8(token, meta.id) ?: return@withContext null
        runCatching { ManifestCodec.fromJson(body) }.getOrNull()
    }

    suspend fun writeManifest(token: String, manifest: BackupManifest): Unit = withContext(Dispatchers.IO) {
        val json = ManifestCodec.toJson(manifest)
        val existing = findByExactName(token, MANIFEST_FILE_NAME)
        if (existing != null) {
            updateJsonContent(token, existing.id, json)
            deleteDuplicatesExcept(token, MANIFEST_FILE_NAME, keepId = existing.id)
        } else {
            val newId = createJsonInAppDataFolder(token, MANIFEST_FILE_NAME, json)
            deleteDuplicatesExcept(token, MANIFEST_FILE_NAME, keepId = newId)
        }
    }

    /** 不変スナップショットを新規作成し、Drive ファイル ID を返す */
    suspend fun uploadImmutableSnapshot(token: String, fileName: String, json: String): String =
        withContext(Dispatchers.IO) {
            createJsonInAppDataFolder(token, fileName, json)
        }

    suspend fun downloadRequired(token: String, fileId: String, label: String): String =
        withContext(Dispatchers.IO) {
            downloadUtf8(token, fileId)
                ?: throw IllegalStateException("Driveから $label を読み込めませんでした")
        }

    suspend fun hasBackupFiles(token: String): Boolean = withContext(Dispatchers.IO) {
        readManifest(token)?.hasAnySnapshot() == true
    }

    /**
     * manifest が参照しないスナップショットと、旧形式ファイルを削除する。
     * [manifest.json] 自体は残す。
     */
    suspend fun pruneUnreferenced(token: String, keepFileIds: Set<String>): Unit = withContext(Dispatchers.IO) {
        val keep = buildSet {
            addAll(keepFileIds)
            findByExactName(token, MANIFEST_FILE_NAME)?.id?.let { add(it) }
        }
        listJsonFiles(token).forEach { meta ->
            if (meta.id in keep) return@forEach
            val legacy = isLegacyBackupFileName(meta.name)
            val orphanSnapshot = meta.name.startsWith(SNAPSHOT_PREFIX) && meta.name.endsWith(".json")
            if (legacy || orphanSnapshot) {
                runCatching { deleteById(token, meta.id) }
                    .onFailure { Log.w(TAG, "prune delete id=${meta.id} name=${meta.name} failed", it) }
            }
        }
    }

    private fun isLegacyBackupFileName(name: String): Boolean =
        name == "current-month.json" ||
            (name.startsWith("full-") && name.endsWith(".json")) ||
            (name.startsWith("archive-") && name.endsWith(".json"))

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
                        throw DriveHttpException(resp.code, "Drive list failed: ${resp.code}", body)
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

    private suspend fun findByExactName(token: String, name: String): DriveFileMeta? =
        listJsonFiles(token).firstOrNull { it.name == name }

    private fun createJsonInAppDataFolder(token: String, fileName: String, json: String): String {
        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", "application/json")
            .put("parents", JSONArray().put("appDataFolder"))
        val url = "$UPLOAD_BASE/files?uploadType=multipart"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(multipartRelatedBody(metadata.toString(), json))
            .build()
        return http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw DriveHttpException(resp.code, "Drive create failed: ${resp.code}", body)
            }
            JSONObject(body).getString("id")
        }
    }

    private fun updateJsonContent(token: String, fileId: String, json: String) {
        val url = "$UPLOAD_BASE/files/$fileId?uploadType=media"
        val media = json.toRequestBody("application/json; charset=UTF-8".toMediaType())
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(media)
            .build()
        http.newCall(req).execute().use { resp ->
            val errBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw DriveHttpException(resp.code, "Drive upload failed: ${resp.code}", errBody)
            }
        }
    }

    private suspend fun deleteDuplicatesExcept(token: String, fileName: String, keepId: String) {
        listJsonFiles(token)
            .filter { it.name == fileName && it.id != keepId }
            .forEach { meta ->
                runCatching { deleteById(token, meta.id) }
                    .onFailure { Log.w(TAG, "delete duplicate id=${meta.id} failed", it) }
            }
    }

    private fun multipartRelatedBody(metadataJson: String, json: String): RequestBody {
        val boundary = "kakeibo_drive_${System.nanoTime()}"
        val buffer = Buffer()
        buffer.writeUtf8("--$boundary\r\n")
        buffer.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        buffer.writeUtf8(metadataJson)
        buffer.writeUtf8("\r\n")
        buffer.writeUtf8("--$boundary\r\n")
        buffer.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        buffer.writeUtf8(json)
        buffer.writeUtf8("\r\n")
        buffer.writeUtf8("--$boundary--\r\n")
        return buffer.readByteString().toRequestBody("multipart/related; boundary=$boundary".toMediaType())
    }

    private suspend fun downloadUtf8(token: String, fileId: String): String? = withContext(Dispatchers.IO) {
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

    private suspend fun deleteById(token: String, fileId: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API_BASE/files/$fileId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        http.newCall(req).execute().use { }
    }

    private const val TAG = "DriveBackupRepository"
}
