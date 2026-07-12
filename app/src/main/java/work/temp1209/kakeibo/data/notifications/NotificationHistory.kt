package work.temp1209.kakeibo.data.notifications

import android.content.Context
import work.temp1209.kakeibo.data.db.AnalysisNotificationEventEntity
import work.temp1209.kakeibo.data.db.AppDatabase
import work.temp1209.kakeibo.data.db.ReceiptEntity
import java.time.Instant
import java.util.UUID

object NotificationHistory {

    const val TYPE_DONE = "DONE"
    const val TYPE_NEEDS_REVIEW = "NEEDS_REVIEW"
    const val TYPE_FAILED = "FAILED"

    const val MAX_STORED = 100
    const val DISPLAY_LIMIT = 50

    suspend fun record(context: Context, receipt: ReceiptEntity, eventType: String) {
        val dao = AppDatabase.get(context.applicationContext).receiptDao()
        dao.insertNotificationEvent(
            AnalysisNotificationEventEntity(
                eventId = UUID.randomUUID().toString(),
                receiptId = receipt.receiptId,
                eventType = eventType,
                occurredAt = Instant.now().toString(),
                merchantName = receipt.merchantName,
                totalAmountYen = receipt.totalAmountYen,
            ),
        )
        val overflow = dao.countNotificationEvents() - MAX_STORED
        if (overflow > 0) {
            val ids = dao.listOldestNotificationEventIds(overflow)
            if (ids.isNotEmpty()) {
                dao.deleteNotificationEventsByIds(ids)
            }
        }
    }

    fun eventTypeLabel(eventType: String): String = when (eventType) {
        TYPE_DONE -> "解析完了"
        TYPE_NEEDS_REVIEW -> "要確認"
        TYPE_FAILED -> "解析失敗"
        else -> eventType
    }
}

data class NotificationHistoryEntry(
    val event: AnalysisNotificationEventEntity,
    val receipt: ReceiptEntity?,
)
