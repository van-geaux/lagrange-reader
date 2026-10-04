package com.vangeaux.lagrange

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A configured BookOrbit server. Credentials remain in the server session, not here. */
data class ServerProfile(
    val id: String,
    val serverUrl: String,
    val providerId: String = "bookorbit",
    val displayName: String = serverUrl,
    val selectedLibraryId: String? = null,
    val lastUsedAtMillis: Long = 0L
)

const val PROVIDER_BOOKORBIT = "bookorbit"
const val PROVIDER_KOMGA = "komga"

class ServerProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun readAll(): List<ServerProfile> = runCatching {
        val array = JSONArray(preferences.getString(PROFILES_KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toProfile()?.let(::add)
            }
        }.sortedByDescending { it.lastUsedAtMillis }
    }.getOrDefault(emptyList())

    @Synchronized
    fun activeId(): String? = preferences.getString(ACTIVE_ID_KEY, null)

    @Synchronized
    fun active(): ServerProfile? = readAll().firstOrNull { it.id == activeId() }

    @Synchronized
    fun upsert(profile: ServerProfile, makeActive: Boolean = true) {
        val current = readAll().associateBy { it.id }.toMutableMap()
        current[profile.id] = profile
        val editor = preferences.edit().putString(
            PROFILES_KEY,
            JSONArray(current.values.map { it.toJson() }).toString()
        )
        if (makeActive) editor.putString(ACTIVE_ID_KEY, profile.id)
        editor.apply()
    }

    @Synchronized
    fun markActive(profileId: String): ServerProfile? {
        val profile = readAll().firstOrNull { it.id == profileId } ?: return null
        upsert(profile.copy(lastUsedAtMillis = System.currentTimeMillis()), makeActive = true)
        return profile
    }

    @Synchronized
    fun updateSelectedLibrary(profileId: String, libraryId: String?) {
        readAll().firstOrNull { it.id == profileId }?.let {
            upsert(it.copy(selectedLibraryId = libraryId), makeActive = activeId() == profileId)
        }
    }

    @Synchronized
    fun remove(profileId: String) {
        val remaining = readAll().filterNot { it.id == profileId }
        preferences.edit()
            .putString(PROFILES_KEY, JSONArray(remaining.map { it.toJson() }).toString())
            .apply()
        if (activeId() == profileId) {
            preferences.edit().putString(ACTIVE_ID_KEY, remaining.firstOrNull()?.id).apply()
        }
    }

    private fun JSONObject.toProfile(): ServerProfile? {
        val id = optString("id").trim().takeIf { it.isNotBlank() } ?: return null
        val url = optString("serverUrl").trim().takeIf { it.isNotBlank() } ?: return null
        return ServerProfile(
            id = id,
            serverUrl = url,
            providerId = optString("providerId", "bookorbit"),
            displayName = optString("displayName", url).ifBlank { url },
            selectedLibraryId = optString("selectedLibraryId").takeIf { it.isNotBlank() },
            lastUsedAtMillis = optLong("lastUsedAtMillis", 0L)
        )
    }

    private fun ServerProfile.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("serverUrl", serverUrl)
        .put("providerId", providerId)
        .put("displayName", displayName)
        .put("selectedLibraryId", selectedLibraryId)
        .put("lastUsedAtMillis", lastUsedAtMillis)

    private companion object {
        const val PREFERENCES_NAME = "server_profiles"
        const val PROFILES_KEY = "profiles"
        const val ACTIVE_ID_KEY = "active_profile_id"
    }
}

internal fun serverProfileId(serverUrl: String): String =
    normalizeServerUrl(serverUrl)?.lowercase() ?: serverUrl.trim().lowercase()
