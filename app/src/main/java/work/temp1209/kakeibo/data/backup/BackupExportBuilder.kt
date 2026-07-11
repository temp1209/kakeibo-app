package work.temp1209.kakeibo.data.backup

import android.content.Context
import android.content.pm.PackageManager
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

object BackupExportBuilder {

    fun appInfo(context: Context): BackupAppInfo {
        val pm = context.packageManager
        val pkg = context.packageName
        val pi = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        val vc = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
        return BackupAppInfo(
            packageName = pkg,
            versionName = pi.versionName ?: "",
            versionCode = vc,
        )
    }

    fun buildFile(
        context: Context,
        exportType: String,
        rangeStart: Instant,
        rangeEnd: Instant,
        receipts: List<ReceiptEntity>,
        items: List<ReceiptItemEntity>,
    ): KakeiboBackupFile {
        val receiptDtos = receipts.map { it.toDto() }
        val itemDtos = items.map { it.toDto() }
        return KakeiboBackupFile(
            exportType = exportType,
            exportedAt = Instant.now().toString(),
            rangeStart = rangeStart.toString(),
            rangeEnd = rangeEnd.toString(),
            app = appInfo(context),
            data = BackupDataPayload(receipts = receiptDtos, receiptItems = itemDtos),
        )
    }

    /** 当月ウィンドウ: ローカル暦の月初 〜 現在（要件: 当月月初から） */
    fun currentMonthWindow(zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
        val start = YearMonth.now(zone).atDay(1).atStartOfDay(zone).toInstant()
        val end = Instant.now()
        return start to end
    }

    fun receiptTimestamp(receipt: ReceiptEntity): Instant? =
        runCatching { Instant.parse(receipt.receiptDatetime ?: receipt.capturedAt) }.getOrNull()

    /**
     * 日次バックアップ（current-month.json）の対象判定。
     * レシート印字日だけでなく、撮影日・最終更新日が当月なら含める（解析後に印字日が先月になるケース対策）。
     */
    fun isInCurrentMonthExport(receipt: ReceiptEntity, start: Instant, end: Instant): Boolean =
        isInstantInWindow(receipt.receiptDatetime, start, end) ||
            isInstantInWindow(receipt.capturedAt, start, end) ||
            isInstantInWindow(receipt.updatedAt, start, end)

    private fun isInstantInWindow(iso: String?, start: Instant, end: Instant): Boolean {
        val t = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
        return !t.isBefore(start) && !t.isAfter(end)
    }

    fun isReceiptInClosedMonth(receipt: ReceiptEntity, ym: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val t = receiptTimestamp(receipt) ?: return false
        val start = ym.atDay(1).atStartOfDay(zone).toInstant()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        return !t.isBefore(start) && t.isBefore(end)
    }

    private fun ReceiptEntity.toDto() = ReceiptBackupDto(
        receiptId = receiptId,
        inputKind = inputKind,
        receiptDatetime = receiptDatetime,
        capturedAt = capturedAt,
        merchantName = merchantName,
        totalAmountYen = totalAmountYen,
        paymentMethod = paymentMethod,
        paymentServiceName = paymentServiceName,
        analysisStatus = analysisStatus,
        needsReview = needsReview == 1,
        itemsSubtotalYen = itemsSubtotalYen,
        adjustmentYen = adjustmentYen,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        deleteReason = deleteReason,
        backupRevision = backupRevision,
    )

    private fun ReceiptItemEntity.toDto() = ReceiptItemBackupDto(
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
        isAdjustment = isAdjustment == 1,
    )
}
