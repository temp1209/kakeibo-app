package work.temp1209.kakeibo.data.drive

/**
 * ローカルの有効レシート数が Drive 上のバックアップより大幅に少ないとき、
 * データ退行による上書きを防ぐ。
 */
class BackupRegressionException(
    val localActiveCount: Int,
    val remoteActiveCount: Int,
) : Exception(
    "ローカル有効${localActiveCount}件はDrive推定${remoteActiveCount}件より少なすぎます。" +
        "上書きを中止しました。先に復元するか、データを確認してください。",
)
