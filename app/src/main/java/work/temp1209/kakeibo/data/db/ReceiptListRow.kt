package work.temp1209.kakeibo.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class ReceiptListRow(
    @Embedded val receipt: ReceiptEntity,
    @ColumnInfo(name = "weightedNecessity") val weightedNecessity: Double?,
    /** `isAdjustment = 0` の先頭行の `itemName`（`lineIndex` 昇順） */
    @ColumnInfo(name = "firstItemName") val firstItemName: String?,
    /** `isAdjustment = 0` の明細行数 */
    @ColumnInfo(name = "nonAdjustmentItemCount") val nonAdjustmentItemCount: Int?,
)
