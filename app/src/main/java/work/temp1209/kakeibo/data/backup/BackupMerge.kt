package work.temp1209.kakeibo.data.backup

import work.temp1209.kakeibo.data.db.ReceiptDao
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import java.time.Instant

object BackupMerge {

    data class MergeStats(val receiptsApplied: Int, val receiptsSkipped: Int)

    /**
     * 要件: receipts は updatedAt が新しい方を採用。採用時は明細をバックアップ側で置換。
     */
    suspend fun mergeIntoDb(dao: ReceiptDao, payload: BackupDataPayload): MergeStats {
        val itemsByReceipt = payload.receiptItems.groupBy { it.receiptId }
        var applied = 0
        var skipped = 0
        for (dto in payload.receipts) {
            val remote = dto.toEntity()
            val local = dao.getReceiptOrNull(remote.receiptId)
            if (local != null && !isRemoteNewer(remote.updatedAt, local.updatedAt)) {
                skipped++
                continue
            }
            val items = itemsByReceipt[remote.receiptId].orEmpty().map { it.toEntity() }
            dao.replaceReceiptAndItems(remote, items)
            applied++
        }
        return MergeStats(receiptsApplied = applied, receiptsSkipped = skipped)
    }

    suspend fun mergeFilesIntoDb(dao: ReceiptDao, jsonBodiesInOrder: List<String>): MergeStats {
        var totalApplied = 0
        var totalSkipped = 0
        for (body in jsonBodiesInOrder) {
            val file = runCatching { BackupJsonCodec.fromJson(body) }.getOrNull() ?: continue
            val stats = mergeIntoDb(dao, file.data)
            totalApplied += stats.receiptsApplied
            totalSkipped += stats.receiptsSkipped
        }
        return MergeStats(receiptsApplied = totalApplied, receiptsSkipped = totalSkipped)
    }

    private fun isRemoteNewer(remoteUpdatedAt: String, localUpdatedAt: String): Boolean {
        val r = runCatching { Instant.parse(remoteUpdatedAt) }.getOrNull() ?: return false
        val l = runCatching { Instant.parse(localUpdatedAt) }.getOrNull() ?: return true
        return r.isAfter(l)
    }

    private fun ReceiptBackupDto.toEntity() = ReceiptEntity(
        receiptId = receiptId,
        capturedAt = capturedAt ?: receiptDatetime ?: updatedAt,
        receiptDatetime = receiptDatetime,
        analysisStatus = analysisStatus,
        merchantName = merchantName,
        totalAmountYen = totalAmountYen,
        paymentMethod = paymentMethod,
        paymentServiceName = paymentServiceName,
        analysisStartedAt = null,
        analysisCompletedAt = null,
        analysisErrorMessage = null,
        needsReview = if (needsReview) 1 else 0,
        itemsSubtotalYen = itemsSubtotalYen,
        adjustmentYen = adjustmentYen,
        deletedAt = deletedAt,
        deleteReason = deleteReason,
        backupRevision = backupRevision,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ReceiptItemBackupDto.toEntity() = ReceiptItemEntity(
        itemId = itemId,
        receiptId = receiptId,
        lineIndex = lineIndex,
        itemName = itemName,
        quantity = quantity,
        lineTotalYen = lineTotalYen,
        categoryMajor = categoryMajor,
        categoryMinor = categoryMinor,
        necessityScore = necessityScore,
        confidence = confidence,
        isAdjustment = if (isAdjustment) 1 else 0,
    )
}
