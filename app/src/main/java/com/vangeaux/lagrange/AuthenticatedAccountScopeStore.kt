package com.vangeaux.lagrange

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest

/** Stable authenticated identity used to isolate durable data stored on this device. */
internal data class AuthenticatedAccountPrincipal(
    val canonical: String,
    val provenAliases: Set<String> = emptySet()
)

/**
 * Extracts only provider-issued stable identifiers. Display names and email addresses are not
 * suitable account boundaries because the user or server administrator can change them.
 */
internal fun authenticatedAccountPrincipal(
    payload: String,
    stableFields: List<String>,
    candidatePaths: List<List<String>> = listOf(emptyList())
): AuthenticatedAccountPrincipal? {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    val candidates = candidatePaths.mapNotNull { path ->
        path.fold(root as JSONObject?) { current, key -> current?.optJSONObject(key) }
    }
    val aliases = candidates.flatMap { candidate ->
        stableFields.mapNotNull { field ->
            candidate.optString(field).trim().takeIf(String::isNotBlank)
                ?.let { value -> "$field:$value" }
        }
    }.toSet()
    val canonical = stableFields.firstNotNullOfOrNull { field ->
        candidates.firstNotNullOfOrNull { candidate ->
            candidate.optString(field).trim().takeIf(String::isNotBlank)
                ?.let { value -> "$field:$value" }
        }
    } ?: return null
    return AuthenticatedAccountPrincipal(canonical, aliases - canonical)
}

internal class AuthenticatedAccountScopeStore(context: Context) {
    private companion object {
        val monitor = Any()
        val stableAuthoritativeFields = setOf("id", "_id", "userId", "sub")
    }

    private val preferences = context.applicationContext.getSharedPreferences(
        "authenticated_account_scopes",
        Context.MODE_PRIVATE
    )

    fun read(profileId: String): String? = synchronized(monitor) {
        preferences.getString(key(profileId), null)
    }

    /**
     * Binds a profile to an identity returned by the provider's authenticated identity endpoint.
     * A remembered alias is accepted only when the same authoritative payload proves that it is
     * another stable identifier for this account.
     */
    fun writeAuthoritative(
        profileId: String,
        providerId: String,
        principal: String,
        equivalentPrincipals: Set<String> = emptySet()
    ): String = synchronized(monitor) {
        val normalizedPrincipal = principal.trim()
        require(normalizedPrincipal.isNotEmpty()) { "Authenticated account identity is blank." }
        val canonicalScope = authenticatedAccountScope(providerId, normalizedPrincipal)
        val existingScope = preferences.getString(key(profileId), null)
        val existingAuthority = preferences.getString(authorityKey(profileId), null)
        val inactiveScope = preferences.getString(inactiveKey(profileId), null)
        val rememberedBinding = preferences.getString(bindingKey(profileId, canonicalScope), null)
        val provenEquivalentScopes = equivalentPrincipals.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filter(::isStableAuthoritativePrincipal)
            .map { authenticatedAccountScope(providerId, it) }
            .toSet()
        val selectedScope = when {
            rememberedBinding != null -> rememberedBinding
            existingScope == null && inactiveScope != null &&
                inactiveScope in provenEquivalentScopes -> inactiveScope
            existingScope == null -> canonicalScope
            existingScope == canonicalScope -> canonicalScope
            existingAuthority == canonicalScope -> existingScope
            existingAuthority == null && existingScope in provenEquivalentScopes -> existingScope
            else -> canonicalScope
        }
        val editor = preferences.edit()
            .putString(key(profileId), selectedScope)
            .putString(authorityKey(profileId), canonicalScope)
            .putString(bindingKey(profileId, canonicalScope), selectedScope)
            .remove(inactiveKey(profileId))
        provenEquivalentScopes.forEach { equivalentScope ->
            val equivalentBindingKey = bindingKey(profileId, equivalentScope)
            val currentBinding = preferences.getString(equivalentBindingKey, null)
            if (currentBinding == null || currentBinding == selectedScope) {
                editor.putString(equivalentBindingKey, selectedScope)
            }
        }
        check(editor.commit()) { "Unable to persist authenticated account identity." }
        selectedScope
    }

    /** Removes the active selection while preserving the binding for a later sign-in. */
    fun deactivate(profileId: String) {
        synchronized(monitor) {
            val currentScope = preferences.getString(key(profileId), null)
            val editor = preferences.edit().remove(key(profileId))
            if (currentScope != null) editor.putString(inactiveKey(profileId), currentScope)
            check(editor.commit()) { "Unable to deactivate authenticated account identity." }
        }
    }

    internal fun clear(profileId: String) {
        synchronized(monitor) {
            val editor = preferences.edit()
                .remove(key(profileId))
                .remove(authorityKey(profileId))
                .remove(inactiveKey(profileId))
            preferences.all.keys.filter { it.startsWith(bindingPrefix(profileId)) }
                .forEach { editor.remove(it) }
            editor.commit()
        }
    }

    private fun isStableAuthoritativePrincipal(principal: String): Boolean {
        val separator = principal.indexOf(':')
        return separator > 0 && principal.substring(0, separator) in stableAuthoritativeFields
    }

    private fun key(profileId: String): String = Base64.encodeToString(
        profileId.toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE
    )

    private fun authorityKey(profileId: String): String = "authority_${key(profileId)}"

    private fun inactiveKey(profileId: String): String = "inactive_${key(profileId)}"

    private fun bindingPrefix(profileId: String): String = "binding_${key(profileId)}_"

    private fun bindingKey(profileId: String, canonicalScope: String): String =
        "${bindingPrefix(profileId)}$canonicalScope"
}

internal fun authenticatedAccountScope(providerId: String, principal: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest("${providerId.trim()}\u0000${principal.trim()}".toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(32)
