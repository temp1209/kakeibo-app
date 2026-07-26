package work.temp1209.kakeibo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * オフライン対応: retention 経過後の画像クリーンアップ判定（[isImageCleanupEligible]）の検証。
 * 解析待ち（PENDING）・解析中（RUNNING）のレシートは、40日を過ぎても画像を消してはいけない
 * （オフラインで通信待ちのまま長期間キューに残った場合、解析前に元画像を失うと解析が
 * 「画像がありません」で失敗してしまうため）。
 */
class ReceiptRepositoryRetentionTest {

    @Test
    fun pending_receipt_is_not_eligible_for_cleanup() {
        assertFalse(isImageCleanupEligible("PENDING"))
    }

    @Test
    fun running_receipt_is_not_eligible_for_cleanup() {
        assertFalse(isImageCleanupEligible("RUNNING"))
    }

    @Test
    fun done_failed_and_needsReview_receipts_are_eligible_for_cleanup() {
        assertTrue(isImageCleanupEligible("DONE"))
        assertTrue(isImageCleanupEligible("FAILED"))
        assertTrue(isImageCleanupEligible("NEEDS_REVIEW"))
    }

    @Test
    fun missing_receipt_is_eligible_for_cleanup() {
        // レシート行自体が既に削除されている（孤立した画像）場合は掃除対象にする
        assertTrue(isImageCleanupEligible(null))
    }
}

/**
 * オフライン対応: 長期未処理キューの異常検出（[isQueueEntryStale]）の検証。
 * オフラインが原因なら数日以内に解消するはずなので、しきい値を超えたQUEUED/RUNNINGは
 * バグでスタックしているとみなして解析失敗に確定する（ゴミが無期限に残るのを防ぐため）。
 */
class QueueEntryStaleTest {

    private val now = Instant.parse("2026-07-27T00:00:00Z")
    private val staleAfter = Duration.ofDays(7)

    @Test
    fun queued_within_threshold_is_not_stale() {
        val queuedAt = now.minus(Duration.ofDays(3))
        assertFalse(isQueueEntryStale("QUEUED", queuedAt, now, staleAfter))
    }

    @Test
    fun queued_past_threshold_is_stale() {
        val queuedAt = now.minus(Duration.ofDays(8))
        assertTrue(isQueueEntryStale("QUEUED", queuedAt, now, staleAfter))
    }

    @Test
    fun running_past_threshold_is_stale() {
        val queuedAt = now.minus(Duration.ofDays(10))
        assertTrue(isQueueEntryStale("RUNNING", queuedAt, now, staleAfter))
    }

    @Test
    fun done_or_failed_is_never_stale_regardless_of_age() {
        val queuedAt = now.minus(Duration.ofDays(365))
        assertFalse(isQueueEntryStale("DONE", queuedAt, now, staleAfter))
        assertFalse(isQueueEntryStale("FAILED", queuedAt, now, staleAfter))
    }

    @Test
    fun exactly_at_threshold_is_not_yet_stale() {
        val queuedAt = now.minus(staleAfter)
        assertFalse(isQueueEntryStale("QUEUED", queuedAt, now, staleAfter))
    }
}
