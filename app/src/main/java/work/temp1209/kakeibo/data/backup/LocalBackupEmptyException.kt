package work.temp1209.kakeibo.data.backup

/**
 * ローカル DB にエクスポート対象のレシートがないときにエクスポートを拒否する。
 */
class LocalBackupEmptyException : Exception(
    "ローカルにレシートがないため、バックアップするデータがありません。",
)
