package work.temp1209.kakeibo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
