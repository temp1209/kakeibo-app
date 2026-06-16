package work.temp1209.kakeibo.data.drive

import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.UserRecoverableAuthException
import org.json.JSONObject

/**
 * Drive バックアップ失敗時のユーザー向け文言（Snackbar / 設定画面）。
 */
object DriveBackupUserMessages {
    const val SHA1_HINT =
        "Google Cloud Console の Android OAuth クライアントに、現在の debug.keystore の SHA-1 が登録されているか確認してください（docs/EXTERNAL_SETUP.md §6）。"
    const val RELOGIN_HINT =
        "設定でログアウト後、再ログインして drive.appdata スコープを許可してください。"

    fun snackbarMessage(error: Throwable): String = when (error) {
        is LocalBackupEmptyException -> error.message ?: "ローカルにレシートがありません。"
        is BackupRegressionException -> error.message ?: "バックアップの上書きを中止しました。"
        is DriveHttpException -> formatHttpShort(error)
        is UserRecoverableAuthException ->
            "Google認証の追加操作が必要です。再ログインしてください。"
        is GoogleAuthException ->
            "Google認証エラー: ${error.message ?: "不明"}"
        else -> truncate(error.message ?: error.javaClass.simpleName)
    }

    fun recoveryHint(error: Throwable): String? = when (error) {
        is BackupRegressionException -> "Driveのバックアップの方が新しい可能性があります。先に復元してください。"
        is DriveHttpException -> when (error.httpCode) {
            401, 403 -> "$RELOGIN_HINT\n$SHA1_HINT"
            else -> null
        }
        is UserRecoverableAuthException,
        is GoogleAuthException,
        -> RELOGIN_HINT
        else -> null
    }

    fun suggestsRestore(error: Throwable): Boolean = when (error) {
        is LocalBackupEmptyException -> error.remoteBackupExists
        is BackupRegressionException -> true
        else -> false
    }

    fun suggestsReLogin(error: Throwable): Boolean = when (error) {
        is DriveHttpException -> error.httpCode == 401 || error.httpCode == 403
        is UserRecoverableAuthException,
        is GoogleAuthException,
        -> true
        is IllegalStateException ->
            error.message?.contains("drive.appdata", ignoreCase = true) == true
        else -> false
    }

    private fun formatHttpShort(error: DriveHttpException): String {
        val reason = parseGoogleApiReason(error.responseBody)
        val detail = when (reason) {
            "insufficientFilePermissions" -> "ファイル操作の権限がありません"
            "authError" -> "認証エラー"
            "insufficientPermissions" -> "権限が不足しています"
            "notFound" -> "ファイルまたはアップロード先が見つかりません"
            else -> reason?.let { translateReason(it) }
        }
        return buildString {
            append("Driveバックアップ失敗（HTTP ${error.httpCode}）")
            if (!detail.isNullOrBlank()) {
                append(": ")
                append(detail)
            } else if (error.httpCode == 404) {
                append(": ファイルまたはアップロード先が見つかりません")
            }
        }
    }

    private fun translateReason(reason: String): String = when (reason) {
        "rateLimitExceeded" -> "利用上限に達しました"
        "userRateLimitExceeded" -> "利用上限に達しました"
        else -> reason
    }

    private fun parseGoogleApiReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val errors = JSONObject(body).optJSONObject("error")?.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                errors.getJSONObject(0).optString("reason", "").takeIf { it.isNotBlank() }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun truncate(text: String, maxLen: Int = 160): String =
        if (text.length <= maxLen) text else text.take(maxLen - 1) + "…"
}
