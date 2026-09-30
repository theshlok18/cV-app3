package com.shlok.sam.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = runCatching {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sam_secrets",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("sam_secrets_fallback", Context.MODE_PRIVATE)
    }

    fun putKey(providerId: String, apiKey: String) {
        prefs.edit().putString(keyName(providerId), apiKey.trim()).apply()
    }

    fun getKey(providerId: String): String? =
        prefs.getString(keyName(providerId), null)?.takeIf { it.isNotBlank() }

    fun clearKey(providerId: String) {
        prefs.edit().remove(keyName(providerId)).apply()
    }

    fun hasKey(providerId: String): Boolean = !getKey(providerId).isNullOrBlank()

    private fun keyName(providerId: String) = "provider_key_$providerId"
}
