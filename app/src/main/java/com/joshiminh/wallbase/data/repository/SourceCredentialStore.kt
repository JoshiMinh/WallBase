package com.joshiminh.wallbase.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps user-supplied provider keys out of Room and backup exports. The values are
 * encrypted with an Android Keystore-backed key and are intentionally never logged.
 */
@Singleton
class SourceCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun snapshot(): SourceCredentials = SourceCredentials(
        wallhavenApiKey = preferences.getString(WALLHAVEN_API_KEY, null).orEmpty().trim(),
    )

    fun save(credentials: SourceCredentials) {
        preferences.edit()
            .putString(WALLHAVEN_API_KEY, credentials.wallhavenApiKey.ifBlank { null })
            .apply()
    }

    data class SourceCredentials(
        val wallhavenApiKey: String = "",
    ) {
        val hasWallhavenToken: Boolean get() = wallhavenApiKey.isNotBlank()
    }

    private companion object {
        const val FILE_NAME = "source_credentials"
        const val WALLHAVEN_API_KEY = "wallhaven_api_key"
    }
}
