package work.temp1209.kakeibo.data.drive

/**
 * ローカル DB が空なのに Drive に既存バックアップがあるとき、
 * 空データでの上書きを防ぐためにバックアップを拒否する。
 */
class LocalBackupEmptyException(
    val remoteBackupExists: Boolean,
) : Exception(
    if (remoteBackupExists) {
        "ローカルにレシートがありません。Driveの既存バックアップを上書きしません。先に復元してください。"
    } else {
        "ローカルにレシートがないため、バックアップするデータがありません。"
    },
)
