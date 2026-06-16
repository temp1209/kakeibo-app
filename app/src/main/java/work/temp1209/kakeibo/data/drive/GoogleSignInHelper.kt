package work.temp1209.kakeibo.data.drive

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

object GoogleSignInHelper {
    /** Drive API v3 appDataFolder 用スコープ */
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()

    fun hasDriveAppDataScope(account: GoogleSignInAccount): Boolean =
        GoogleSignIn.hasPermissions(account, Scope(DRIVE_APPDATA_SCOPE))
}
