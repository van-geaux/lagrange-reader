package com.vangeaux.lagrange

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

interface ProfileSessionAware {
    suspend fun saveCurrentProfileSession()
    suspend fun restoreCurrentProfileSession(): Boolean
}

/** Encrypted provider session values keyed separately from server profile metadata. */
class ProfileSessionStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("profile_sessions", Context.MODE_PRIVATE)

    fun read(profileId: String): String? = runCatching {
        val encoded = preferences.getString(key(profileId), null) ?: return null
        val packed = Base64.decode(encoded, Base64.DEFAULT)
        val iv = packed.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = packed.copyOfRange(GCM_IV_LENGTH, packed.size)
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    fun write(profileId: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(key(profileId), Base64.encodeToString(packed, Base64.NO_WRAP))
                .apply()
        }
    }

    fun clear(profileId: String) {
        preferences.edit().remove(key(profileId)).apply()
    }

    private fun key(profileId: String): String =
        "session_${Base64.encodeToString(profileId.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)}"

    private fun key(): java.security.Key {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            val generator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        }
        return (store.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private companion object {
        const val KEY_ALIAS = "lagrange_profile_sessions"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}
