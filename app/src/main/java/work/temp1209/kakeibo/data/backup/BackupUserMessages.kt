package work.temp1209.kakeibo.data.backup

object BackupUserMessages {

    fun exportSuccess(): String = "バックアップを保存しました"

    fun mergeResult(stats: BackupMerge.MergeStats): String = buildString {
        append("復元マージ完了（反映 ${stats.receiptsApplied}件、スキップ ${stats.receiptsSkipped}件）")
        if (stats.jsonParseFailures > 0) {
            append("、JSON無効 ${stats.jsonParseFailures}件")
        }
    }

    fun snackbarMessage(error: Throwable): String = when (error) {
        is LocalBackupEmptyException -> error.message ?: "エクスポートするデータがありません"
        is BackupRegressionException -> error.message ?: "バックアップ件数を確認してください"
        else -> error.message ?: "バックアップ処理に失敗しました"
    }

    fun importConfirmMessage(localActive: Int, backupActive: Int, exportedAt: String?): String =
        buildString {
            if (localActive == 0) {
                append("バックアップからデータを復元します。")
            } else {
                append("既存データとマージします。新しい方の更新日時が採用されます。")
            }
            append("\n\nバックアップ: 有効 ${backupActive}件")
            append("\nローカル: 有効 ${localActive}件")
            if (!exportedAt.isNullOrBlank()) {
                append("\nエクスポート日時: $exportedAt")
            }
        }
}
