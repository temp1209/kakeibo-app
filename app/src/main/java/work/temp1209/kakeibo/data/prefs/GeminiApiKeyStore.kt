package work.temp1209.kakeibo.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class GeminiApiKeyStore(context: Context) {
    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasKey(): Boolean = prefs.contains(KEY_API_KEY)

    fun saveKey(apiKey: String) {
        if (apiKey.isBlank()) return
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun deleteKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    fun readKeyOrNull(): String? = prefs.getString(KEY_API_KEY, null)

    companion object {
        const val MISSING_KEY_USER_MESSAGE =
            "Gemini APIキーが未設定です。設定画面で入力してください。"

        private const val FILE_NAME = "secrets"
        private const val KEY_API_KEY = "gemini_api_key"
    }
}

