package com.vangeaux.lagrange

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** A username/password pair saved for one configured server profile. */
internal data class SavedLoginCredentials(
    val username: String,
    val password: String
)

internal fun savedLoginCredentialsToJson(credentials: SavedLoginCredentials): String =
    JSONObject()
        .put("username", credentials.username)
        .put("password", credentials.password)
        .toString()

internal fun savedLoginCredentialsFromJson(json: String): SavedLoginCredentials? = runCatching {
    val payload = JSONObject(json)
    SavedLoginCredentials(
        username = payload.optString("username").trim(),
        password = payload.optString("password")
    ).takeIf { it.username.isNotBlank() && it.password.isNotBlank() }
}.getOrNull()

/** Storage contract for optionally remembered username/password credentials. */
internal interface SavedLoginCredentialsRepository {
    fun read(profileId: String): SavedLoginCredentials?

    fun write(profileId: String, credentials: SavedLoginCredentials): Boolean

    fun clear(profileId: String)
}

/**
 * Stores saved login credentials encrypted with an Android Keystore AES/GCM key.
 *
 * Credentials are deliberately kept separate from provider session tokens and server profile
 * metadata. A profile ID is the storage scope, so two accounts on the same server URL cannot share
 * a remembered password.
 */
internal class SavedLoginCredentialsStore(context: Context) : SavedLoginCredentialsRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(profileId: String): SavedLoginCredentials? = runCatching {
        val encoded = preferences.getString(key(profileId), null) ?: return null
        val packed = Base64.decode(encoded, Base64.DEFAULT)
        if (packed.size <= GCM_IV_LENGTH) return null
        val iv = packed.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = packed.copyOfRange(GCM_IV_LENGTH, packed.size)
        val json = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }.doFinal(encrypted).toString(Charsets.UTF_8)
        savedLoginCredentialsFromJson(json)
    }.getOrNull()

    override fun write(profileId: String, credentials: SavedLoginCredentials): Boolean {
        if (profileId.isBlank() || credentials.username.isBlank() || credentials.password.isBlank()) {
            return false
        }
        return runCatching {
            val payload = savedLoginCredentialsToJson(credentials).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key())
            }
            val packed = cipher.iv + cipher.doFinal(payload)
            preferences.edit()
                .putString(key(profileId), Base64.encodeToString(packed, Base64.NO_WRAP))
                .commit()
        }.getOrDefault(false)
    }

    override fun clear(profileId: String) {
        preferences.edit().remove(key(profileId)).apply()
    }

    private fun key(profileId: String): String =
        "credentials_${Base64.encodeToString(
            profileId.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )}"

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
        const val PREFERENCES_NAME = "saved_login_credentials"
        const val KEY_ALIAS = "lagrange_saved_login_credentials"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}
