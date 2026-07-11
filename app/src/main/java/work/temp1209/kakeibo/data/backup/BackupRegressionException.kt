package work.temp1209.kakeibo.data.backup

/**
 * インポートするバックアップの有効件数がローカルより大幅に多いとき、
 * 意図しない復元を防ぐための確認用。
 */
class BackupRegressionException(
    val localActiveCount: Int,
    val backupActiveCount: Int,
) : Exception(
    "バックアップ（有効${backupActiveCount}件）はローカル（有効${localActiveCount}件）より多く含まれています。" +
        "意図した復元か確認してください。",
)
