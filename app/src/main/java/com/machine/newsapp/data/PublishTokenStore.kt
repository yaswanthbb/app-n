@file:Suppress("DEPRECATION") // EncryptedSharedPreferences is explicitly requested.

package com.machine.newsapp.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PublishTokenStore {
    suspend fun hasToken(): Boolean
    suspend fun readForDelete(): String?
    suspend fun save(token: String)
    suspend fun clear()
}

@Suppress("DEPRECATION") // Explicitly requested storage API; key stays in Android Keystore.
class EncryptedPublishTokenStore(context: Context) : PublishTokenStore {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext, "publish_credentials", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    override suspend fun hasToken() = withContext(Dispatchers.IO) { preferences.contains("publish_token") }
    override suspend fun readForDelete() = withContext(Dispatchers.IO) { preferences.getString("publish_token", null) }
    override suspend fun save(token: String) = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && token.none { it == '\n' || it == '\r' }) { "Enter a valid publishing token." }
        check(preferences.edit().putString("publish_token", token).commit()) { "Couldn't save the token securely." }
    }
    override suspend fun clear() = withContext(Dispatchers.IO) {
        check(preferences.edit().remove("publish_token").commit()) { "Couldn't remove the saved token." }
    }
}
