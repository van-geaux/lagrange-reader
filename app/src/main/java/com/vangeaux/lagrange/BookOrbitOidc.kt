package com.vangeaux.lagrange

import java.util.Base64
import org.json.JSONArray
import java.io.IOException
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

data class BookOrbitOidcProvider(
    val slug: String,
    val enabled: Boolean,
    val label: String,
    val clientId: String,
    val scopes: String,
    val authorizationEndpoint: String?
)

data class BookOrbitOidcState(
    val state: String,
    val authorizationEndpoint: String?
)

data class BookOrbitOidcTransaction(
    val provider: BookOrbitOidcProvider,
    val serverUrl: String,
    val redirectUri: String,
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val authorizationUrl: String,
    val createdAtMillis: Long
)

data class BookOrbitOidcCallback(
    val code: String,
    val state: String,
    val nonce: String?
)

internal object BookOrbitOidc {
    private const val CALLBACK_PATH = "/oauth2-callback"
    private const val DEFAULT_SCOPES = "openid profile email"
    private const val NONCE_BYTES = 32
    private const val VERIFIER_BYTES = 32
    private val secureRandom = SecureRandom()

    fun parseProviders(payload: String): List<BookOrbitOidcProvider> {
        val array = when (val root = runCatching { org.json.JSONTokener(payload).nextValue() }.getOrNull()) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("providers") ?: return emptyList()
            else -> return emptyList()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val slug = item.optString("slug").trim()
                val clientId = item.optString("clientId").trim()
                if (slug.isBlank() || clientId.isBlank()) continue
                val label = item.optString("displayName").trim()
                    .ifBlank { item.optString("name").trim() }
                    .ifBlank { slug }
                add(
                    BookOrbitOidcProvider(
                        slug = slug,
                        enabled = item.optBoolean("enabled", false),
                        label = label,
                        clientId = clientId,
                        scopes = item.optString("scopes").trim().ifBlank { DEFAULT_SCOPES },
                        authorizationEndpoint = item.optString("authorizationEndpoint")
                            .trim().takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    fun parseState(payload: String): BookOrbitOidcState? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val state = root.optString("state").trim().takeIf { it.isNotBlank() } ?: return null
        return BookOrbitOidcState(
            state = state,
            authorizationEndpoint = root.optString("authorizationEndpoint")
                .trim().takeIf { it.isNotBlank() }
        )
    }

    fun buildTransaction(
        provider: BookOrbitOidcProvider,
        serverUrl: String,
        state: BookOrbitOidcState,
        nowMillis: Long = System.currentTimeMillis()
    ): BookOrbitOidcTransaction? {
        val redirectUri = callbackUri(serverUrl) ?: return null
        val authorizationEndpoint = state.authorizationEndpoint ?: provider.authorizationEndpoint ?: return null
        val nonce = randomUrlSafe(NONCE_BYTES)
        val codeVerifier = randomUrlSafe(VERIFIER_BYTES)
        val challenge = pkceChallenge(codeVerifier)
        val scopes = normalizeScopes(provider.scopes)
        val query = linkedMapOf(
            "response_type" to "code",
            "client_id" to provider.clientId,
            "redirect_uri" to redirectUri,
            "scope" to scopes,
            "state" to state.state,
            "nonce" to nonce,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256"
        )
        val separator = if (authorizationEndpoint.contains('?')) '&' else '?'
        val authorizationUrl = authorizationEndpoint + separator + query.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        return BookOrbitOidcTransaction(
            provider = provider,
            serverUrl = serverUrl,
            redirectUri = redirectUri,
            state = state.state,
            nonce = nonce,
            codeVerifier = codeVerifier,
            authorizationUrl = authorizationUrl,
            createdAtMillis = nowMillis
        )
    }

    fun callbackUri(serverUrl: String): String? {
        val base = normalizedServerUrl(serverUrl) ?: return null
        return base + CALLBACK_PATH
    }

    fun parseAndValidateCallback(
        callbackUrl: String,
        expectedRedirectUri: String,
        expectedState: String
    ): Result<BookOrbitOidcCallback> {
        val callback = runCatching { URI(callbackUrl) }.getOrElse {
            return Result.failure(BookOrbitOidcException("The sign-in callback was malformed."))
        }
        val expected = runCatching { URI(expectedRedirectUri) }.getOrElse {
            return Result.failure(BookOrbitOidcException("The sign-in callback target was invalid."))
        }
        if (!sameExactOriginAndPath(callback, expected) || !callback.rawFragment.isNullOrBlank()) {
            return Result.failure(BookOrbitOidcException("The sign-in callback came from an unexpected location."))
        }
        val params = parseQuery(callback.rawQuery.orEmpty())
        if (params["error"] != null) {
            return Result.failure(BookOrbitOidcException("The identity provider cancelled sign-in."))
        }
        val code = params["code"].orEmpty().takeIf { it.isNotBlank() }
            ?: return Result.failure(BookOrbitOidcException("The identity provider did not return a sign-in code."))
        val state = params["state"].orEmpty().takeIf { it.isNotBlank() }
            ?: return Result.failure(BookOrbitOidcException("The sign-in response did not contain state."))
        if (state != expectedState) {
            return Result.failure(BookOrbitOidcException("The sign-in response could not be verified."))
        }
        return Result.success(BookOrbitOidcCallback(code, state, params["nonce"]?.takeIf { it.isNotBlank() }))
    }

    fun isExactCallbackUrl(callbackUrl: String, expectedRedirectUri: String): Boolean {
        val callback = runCatching { URI(callbackUrl) }.getOrNull() ?: return false
        val expected = runCatching { URI(expectedRedirectUri) }.getOrNull() ?: return false
        return sameExactOriginAndPath(callback, expected) && callback.rawFragment.isNullOrBlank()
    }

    fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun normalizeScopes(scopes: String): String {
        val parts = scopes.split(Regex("\\s+"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toMutableList()
        if (parts.none { it.equals("offline_access", ignoreCase = true) }) {
            parts += "offline_access"
        }
        return parts.joinToString(" ").ifBlank { "$DEFAULT_SCOPES offline_access" }
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun normalizedServerUrl(serverUrl: String): String? {
        val trimmed = serverUrl.trim().trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return trimmed
    }

    private fun sameExactOriginAndPath(actual: URI, expected: URI): Boolean {
        return actual.scheme.equals(expected.scheme, ignoreCase = true) &&
            actual.host.equals(expected.host, ignoreCase = true) &&
            effectivePort(actual) == effectivePort(expected) &&
            actual.path == expected.path
    }

    private fun effectivePort(uri: URI): Int = if (uri.port != -1) uri.port else when (uri.scheme.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }

    private fun parseQuery(rawQuery: String): Map<String, String> = buildMap {
        if (rawQuery.isBlank()) return@buildMap
        rawQuery.split('&').forEach { item ->
            val separator = item.indexOf('=')
            val rawKey = if (separator >= 0) item.substring(0, separator) else item
            val rawValue = if (separator >= 0) item.substring(separator + 1) else ""
            val key = decode(rawKey)
            if (key.isNotBlank() && !containsKey(key)) put(key, decode(rawValue))
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault("")
}

class BookOrbitOidcException(message: String) : IOException(message)
