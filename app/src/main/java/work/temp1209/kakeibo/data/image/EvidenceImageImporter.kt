package work.temp1209.kakeibo.data.image

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object EvidenceImageImporter {
    /**
     * 要件: 非JPEGも含め、内部保存は JPEG（長辺1600・品質85）。
     * 返り値は internal storage の file Uri。
     */
    fun importToInternalJpeg(context: Context, source: Uri): Uri {
        val tmp = File(context.cacheDir, "evidence_${UUID.randomUUID()}.bin")
        context.contentResolver.openInputStream(source)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IllegalStateException("Failed to open image stream")

        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val outFile = File(dir, "${UUID.randomUUID()}.jpg")
        try {
            val resized = resizeJpegLongSide(tmp, maxLongSidePx = 1600)
            saveJpeg(resized, outFile, quality = 85)
        } finally {
            runCatching { tmp.delete() }
        }
        return Uri.fromFile(outFile)
    }
}

