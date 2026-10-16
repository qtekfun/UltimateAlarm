package com.qtekfun.ultimatealarm.feature.backup

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the encrypted part of an export carries (RF-B2). */
@Serializable
data class NextcloudAccount(val server: String = "", val username: String = "", val password: String = "", val folder: String = DEFAULT_FOLDER) {
    val configured: Boolean get() = server.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    companion object {
        const val DEFAULT_FOLDER = "UltimateAlarm"
    }
}

/**
 * Nextcloud settings in credential-protected storage (they are not needed to ring); the app password is sealed with a
 * key that never leaves the Android Keystore (§9), as in UltimatePhone.
 */
class NextcloudSettings(context: Context, private val vault: SecretVault = SecretVault(KeystoreSecretCipher())) {
    private val prefs = context.applicationContext.getSharedPreferences("nextcloud", Context.MODE_PRIVATE)

    var account: NextcloudAccount
        get() = NextcloudAccount(
            server = prefs.getString(KEY_SERVER, "").orEmpty(),
            username = prefs.getString(KEY_USER, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, null)?.let(vault::open).orEmpty(),
            folder = prefs.getString(KEY_FOLDER, NextcloudAccount.DEFAULT_FOLDER).orEmpty().ifBlank { NextcloudAccount.DEFAULT_FOLDER }
        )
        set(value) = prefs.edit {
            putString(KEY_SERVER, value.server.trim())
            putString(KEY_USER, value.username.trim())
            putString(KEY_FOLDER, value.folder.trim().trim('/'))
            if (value.password.isBlank()) remove(KEY_PASSWORD) else putString(KEY_PASSWORD, vault.seal(value.password))
        }

    /** RF-B3: automatic backup after changes and daily. */
    var auto: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO, value) }

    /** RF-B3: copies kept on the server (10 by default). */
    var keep: Int
        get() = prefs.getInt(KEY_KEEP, DEFAULT_KEEP)
        set(value) = prefs.edit { putInt(KEY_KEEP, value.coerceIn(1, MAX_KEEP)) }

    var lastBackup: Long
        get() = prefs.getLong(KEY_LAST, 0)
        set(value) = prefs.edit { putLong(KEY_LAST, value) }

    var lastError: String?
        get() = prefs.getString(KEY_ERROR, null)
        set(value) = prefs.edit { putString(KEY_ERROR, value) }

    fun accountJson(): String = Json.encodeToString(account)

    fun restoreAccount(json: String) {
        account = Json { ignoreUnknownKeys = true }.decodeFromString<NextcloudAccount>(json)
    }

    private companion object {
        const val DEFAULT_KEEP = 10
        const val MAX_KEEP = 100
        const val KEY_SERVER = "server"
        const val KEY_USER = "user"
        const val KEY_PASSWORD = "password"
        const val KEY_FOLDER = "folder"
        const val KEY_AUTO = "auto"
        const val KEY_KEEP = "keep"
        const val KEY_LAST = "last_backup"
        const val KEY_ERROR = "last_error"
    }
}
