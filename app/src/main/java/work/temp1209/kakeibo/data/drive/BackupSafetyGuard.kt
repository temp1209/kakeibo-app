package work.temp1209.kakeibo.data.drive

/**
 * Drive バックアップの上書き・削除前に安全性を検証する。
 */
object BackupSafetyGuard {

    /** 論理削除などで多少減っても許容する件数差 */
    private const val REGRESSION_MARGIN = 2

    suspend fun assertSafeToUpload(
        token: String,
        localActiveCount: Int,
        exportActiveCount: Int,
    ) {
        val manifest = DriveBackupRepository.readManifest(token)
        if (manifest == null || !manifest.hasAnySnapshot()) {
            return
        }
        if (localActiveCount == 0) {
            throw LocalBackupEmptyException(remoteBackupExists = true)
        }
        val remoteActive = manifest.estimatedRemoteActiveCount()
        if (remoteActive == 0) {
            return
        }

        if (localActiveCount + REGRESSION_MARGIN < remoteActive) {
            throw BackupRegressionException(
                localActiveCount = localActiveCount,
                remoteActiveCount = remoteActive,
            )
        }

        if (exportActiveCount + REGRESSION_MARGIN < remoteActive && exportActiveCount < localActiveCount) {
            throw BackupRegressionException(
                localActiveCount = exportActiveCount,
                remoteActiveCount = remoteActive,
            )
        }
    }
}
