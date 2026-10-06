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

    /** 履歴の表示専用: 失敗の履歴だが、その後の再送信・修正で解決済み */
    const val DISPLAY_FAILED_THEN_DONE = "FAILED_THEN_DONE"

    /** 履歴の表示専用: 失敗の履歴で、再送信後の解析を待っている */
    const val DISPLAY_FAILED_RETRYING = "FAILED_RETRYING"

    fun eventTypeLabel(eventType: String): String = when (eventType) {
        TYPE_DONE -> "解析完了"
        TYPE_NEEDS_REVIEW -> "要確認"
        TYPE_FAILED -> "解析失敗"
        DISPLAY_FAILED_THEN_DONE -> "解析失敗→完了"
        DISPLAY_FAILED_RETRYING -> "解析失敗→再送信中"
        else -> eventType
    }

    /**
     * 履歴のチップに出す種別。履歴は「その時に起きたこと」の記録だが、失敗の履歴がいつまでも
     * 「解析失敗」のままだと、再送信で解決したあとも失敗に見えてしまう。
     * そこで失敗の履歴は、レシートの現在の状態（[receiptStatus]）を反映して表示する。
     * 削除済みなど現在の状態が分からないときは、記録どおり失敗のまま。
     */
    fun historyDisplayType(eventType: String, receiptStatus: String?): String {
        if (eventType != TYPE_FAILED) return eventType
        return when (receiptStatus) {
            "DONE" -> DISPLAY_FAILED_THEN_DONE
            "PENDING", "RUNNING" -> DISPLAY_FAILED_RETRYING
            "NEEDS_REVIEW" -> TYPE_NEEDS_REVIEW
            else -> TYPE_FAILED
        }
    }
}

data class NotificationHistoryEntry(
    val event: AnalysisNotificationEventEntity,
    val receipt: ReceiptEntity?,
)
