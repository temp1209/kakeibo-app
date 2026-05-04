package work.temp1209.kakeibo.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class ReceiptListRow(
    @Embedded val receipt: ReceiptEntity,
    @ColumnInfo(name = "weightedNecessity") val weightedNecessity: Double?,
)
