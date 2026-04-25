package work.temp1209.kakeibo.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptImageEntity
import java.time.Instant
import java.util.UUID

class ReceiptRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).receiptDao()

    suspend fun savePendingReceipt(imageUri: Uri): String = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val receiptId = UUID.randomUUID().toString()

        val bytes = runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                }
                total
            }
        }.getOrNull() ?: 0L

        val (w, h) = runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                opts.outWidth to opts.outHeight
            }
        }.getOrNull()?.let { (ww, hh) -> ww to hh } ?: (null to null)

        dao.upsertReceipt(
            ReceiptEntity(
                receiptId = receiptId,
                capturedAt = now,
                analysisStatus = "PENDING",
                createdAt = now,
                updatedAt = now,
            )
        )
        dao.upsertReceiptImage(
            ReceiptImageEntity(
                receiptId = receiptId,
                localUri = imageUri.toString(),
                byteSize = bytes,
                width = w,
                height = h,
            )
        )

        receiptId
    }

    suspend fun listReceipts() = withContext(Dispatchers.IO) {
        dao.listReceipts()
    }
}

