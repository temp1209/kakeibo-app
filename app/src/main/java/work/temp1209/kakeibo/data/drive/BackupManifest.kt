package work.temp1209.kakeibo.data.drive

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import work.temp1209.kakeibo.data.backup.KakeiboBackupFile
import java.time.Instant

/** Drive 上の唯一の可変ファイル `manifest.json` のスキーマ（v2） */
data class BackupManifest(
    @SerializedName("manifestSchemaVersion") val manifestSchemaVersion: String = "2.0",
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("currentMonth") val currentMonth: ManifestSnapshotRef? = null,
    @SerializedName("fullSnapshot") val fullSnapshot: ManifestSnapshotRef? = null,
    @SerializedName("previousFullSnapshot") val previousFullSnapshot: ManifestSnapshotRef? = null,
    @SerializedName("archives") val archives: List<ManifestArchiveRef> = emptyList(),
) {
    fun hasAnySnapshot(): Boolean =
        currentMonth != null || fullSnapshot != null || previousFullSnapshot != null || archives.isNotEmpty()

    fun estimatedRemoteActiveCount(): Int = listOfNotNull(
        fullSnapshot?.activeReceiptCount,
        currentMonth?.activeReceiptCount,
    ).maxOrNull() ?: 0

    fun allReferencedFileIds(): Set<String> = buildSet {
        currentMonth?.fileId?.let { add(it) }
        fullSnapshot?.fileId?.let { add(it) }
        previousFullSnapshot?.fileId?.let { add(it) }
        archives.forEach { add(it.fileId) }
    }
}

data class ManifestSnapshotRef(
    @SerializedName("fileId") val fileId: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("exportedAt") val exportedAt: String,
    @SerializedName("activeReceiptCount") val activeReceiptCount: Int,
    @SerializedName("maxUpdatedAt") val maxUpdatedAt: String?,
)

data class ManifestArchiveRef(
    @SerializedName("yearMonth") val yearMonth: String,
    @SerializedName("fileId") val fileId: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("activeReceiptCount") val activeReceiptCount: Int,
)

object ManifestCodec {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun toJson(manifest: BackupManifest): String = gson.toJson(manifest)

    fun fromJson(json: String): BackupManifest = gson.fromJson(json, BackupManifest::class.java)
}

object SnapshotFileNames {
    fun forExport(exportTypeSuffix: String, extra: String = ""): String {
        val ts = Instant.now().toString().replace(":", "-")
        return "snap-$ts-$exportTypeSuffix$extra.json"
    }
}

fun KakeiboBackupFile.toManifestRef(fileId: String, fileName: String): ManifestSnapshotRef {
    val active = data.receipts.count { it.deletedAt == null }
    val maxUpdated = data.receipts
        .mapNotNull { runCatching { Instant.parse(it.updatedAt) }.getOrNull() }
        .maxOrNull()
        ?.toString()
    return ManifestSnapshotRef(
        fileId = fileId,
        fileName = fileName,
        exportedAt = exportedAt,
        activeReceiptCount = active,
        maxUpdatedAt = maxUpdated,
    )
}
