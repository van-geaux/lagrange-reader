package com.vangeaux.lagrange.provider.opds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.Charset
import java.util.Base64

internal class OpdsHttpStatusException(
    val statusCode: Int,
    message: String
) : IOException(message)

internal fun opdsBasicAuthorization(username: String, password: String): String =
    "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))

internal data class OpdsFetchedFeed(val url: String, val xml: String)

/** Origin-constrained OPDS transport with optional profile-scoped HTTP Basic authorization. */
internal class OpdsFeedClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val authorization: () -> String? = { null }
) {
    suspend fun fetchFeed(url: String, feedUrl: String, authorizationHeader: String? = authorization()): OpdsFetchedFeed = withContext(Dispatchers.IO) {
        val resolved = trusted(url, feedUrl)
        val response = getBytes(resolved, feedUrl, MAX_FEED_BYTES, authorizationHeader)
        OpdsFetchedFeed(response.first, String(response.second, response.third))
    }

    suspend fun fetchImage(url: String, feedUrl: String, authorizationHeader: String? = authorization()): ByteArray? = withContext(Dispatchers.IO) {
        val resolved = trusted(url, feedUrl)
        getBytes(resolved, feedUrl, MAX_IMAGE_BYTES, authorizationHeader).second
    }

    suspend fun stream(
        url: String,
        feedUrl: String,
        consume: (java.io.InputStream, Long?) -> Unit,
        authorizationHeader: String? = authorization()
    ) = withContext(Dispatchers.IO) { streamBlocking(url, feedUrl, consume, authorizationHeader) }

    fun streamBlocking(
        url: String,
        feedUrl: String,
        consume: (java.io.InputStream, Long?) -> Unit,
        authorizationHeader: String? = authorization()
    ) {
        var requestUrl = trusted(url, feedUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val request = Request.Builder().url(requestUrl).get().apply {
                authorizationHeader?.let { header("Authorization", it) }
            }.build()
            client.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    if (redirectCount == MAX_REDIRECTS) throw IOException("The OPDS server redirected too many times.")
                    val location = response.header("Location") ?: throw IOException("The OPDS server returned an invalid redirect.")
                    requestUrl = OpdsAtomParser.resolveTrustedUrl(requestUrl, location, feedUrl)
                        ?: throw IOException("The OPDS server redirected outside the configured origin.")
                } else {
                    if (response.code == 401 || response.code == 403) {
                        throw OpdsHttpStatusException(response.code, authenticationMessage(response.code, authorizationHeader))
                    }
                    if (!response.isSuccessful) {
                        throw OpdsHttpStatusException(response.code, "OPDS acquisition failed with HTTP ${response.code}.")
                    }
                    val body = response.body ?: throw IOException("The OPDS server returned an empty acquisition.")
                    if (!sameOrigin(response.request.url.toString(), feedUrl)) {
                        throw IOException("The OPDS acquisition escaped the configured origin.")
                    }
                    body.byteStream().use { input -> consume(input, body.contentLength().takeIf { it >= 0L }) }
                    return
                }
            }
        }
        throw IOException("Unable to load the OPDS acquisition.")
    }

    fun resolveLink(baseUrl: String, href: String, feedUrl: String): String? =
        OpdsAtomParser.resolveTrustedUrl(baseUrl, href, feedUrl)

    private fun trusted(url: String, feedUrl: String): String =
        OpdsAtomParser.resolveTrustedUrl(feedUrl, url, feedUrl)
            ?: throw IOException("The OPDS URL is outside the configured server origin.")

    private suspend fun getBytes(
        url: String,
        feedUrl: String,
        maxBytes: Int,
        authorizationHeader: String?
    ): Triple<String, ByteArray, Charset> {
        var requestUrl = trusted(url, feedUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/atom+xml, application/xml;q=0.9, image/*;q=0.8")
                .apply { authorizationHeader?.let { header("Authorization", it) } }
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.use { current ->
                val status = current.code
                if (status in 300..399) {
                    if (redirectCount == MAX_REDIRECTS) throw IOException("The OPDS server redirected too many times.")
                    val location = current.header("Location") ?: throw IOException("The OPDS server returned an invalid redirect.")
                    requestUrl = OpdsAtomParser.resolveTrustedUrl(requestUrl, location, feedUrl)
                        ?: throw IOException("The OPDS server redirected outside the configured origin.")
                } else {
                    if (status == 401 || status == 403) {
                        throw OpdsHttpStatusException(status, authenticationMessage(status, authorizationHeader))
                    }
                    if (!current.isSuccessful) {
                        throw OpdsHttpStatusException(status, "OPDS request failed with HTTP $status.")
                    }
                    val body = current.body ?: throw IOException("The OPDS server returned an empty response.")
                    val contentLength = body.contentLength()
                    val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    if (contentLength > maxBytes) throw IOException("The OPDS response is too large.")
                    val output = ByteArrayOutputStream()
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxBytes) throw IOException("The OPDS response is too large.")
                            output.write(buffer, 0, read)
                        }
                    }
                    val responseUrl = current.request.url.toString()
                    if (!sameOrigin(responseUrl, feedUrl)) throw IOException("The OPDS response escaped the configured origin.")
                    return Triple(responseUrl, output.toByteArray(), charset)
                }
            }
        }
        throw IOException("Unable to load the OPDS feed.")
    }

    private fun sameOrigin(first: String, second: String): Boolean = runCatching {
        val a = URI(first)
        val b = URI(second)
        a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && port(a) == port(b)
    }.getOrDefault(false)

    private fun authenticationMessage(status: Int, authorizationHeader: String?): String = when (status) {
        401 -> if (authorizationHeader == null) {
            "This OPDS feed requires authentication."
        } else {
            "OPDS authentication failed."
        }
        else -> "OPDS access is forbidden. Check that the catalog is enabled and the account has access."
    }

    private fun port(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", true) -> 443
        else -> 80
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_FEED_BYTES = 4 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    }
}
