package work.temp1209.kakeibo.data.backup

import com.google.gson.GsonBuilder
import java.time.Instant

object BackupJsonCodec {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun toJson(file: KakeiboBackupFile): String = gson.toJson(file)

    fun fromJson(json: String): KakeiboBackupFile = gson.fromJson(json, KakeiboBackupFile::class.java)
}
