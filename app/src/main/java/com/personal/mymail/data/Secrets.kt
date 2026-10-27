package com.personal.mymail.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores account passwords encrypted with a key held in the Android Keystore. */
class Secrets(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "mymail_secrets",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setPassword(accountId: Long, password: String) {
        prefs.edit().putString("pw_$accountId", password).apply()
    }

    fun getPassword(accountId: Long): String = prefs.getString("pw_$accountId", "") ?: ""

    fun remove(accountId: Long) {
        prefs.edit().remove("pw_$accountId").apply()
    }
}
