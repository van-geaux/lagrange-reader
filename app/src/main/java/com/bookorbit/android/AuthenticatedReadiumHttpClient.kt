package com.bookorbit.android

import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse

internal class AuthenticatedReadiumHttpClient(
    private val delegate: HttpClient,
    private val headersProvider: suspend (AbsoluteUrl) -> Map<String, String>,
    private val recoverAuthentication: suspend () -> Boolean
) : HttpClient {
    override suspend fun stream(
        request: HttpRequest
    ): Try<HttpStreamResponse, HttpError> {
        val first = delegate.stream(request.withCurrentAuthentication())
        val error = first.failureOrNull()
        if (!isReadiumAuthenticationFailure(error)) {
            return first
        }
        if (!recoverAuthentication()) {
            return first
        }
        return delegate.stream(request.withCurrentAuthentication())
    }

    private suspend fun HttpRequest.withCurrentAuthentication(): HttpRequest {
        val headers = headersProvider(url)
        if (headers.isEmpty()) return this
        return copy {
            val mergedHeaders = mergeReadiumRequestHeaders(this.headers, headers)
            this.headers.clear()
            mergedHeaders.forEach { (name, values) ->
                this.headers[name] = values.toMutableList()
            }
        }
    }
}

internal fun isReadiumAuthenticationFailure(error: HttpError?): Boolean =
    error is HttpError.ErrorResponse && error.status.code in setOf(401, 403)

internal fun mergeReadiumRequestHeaders(
    existing: Map<String, List<String>>,
    currentAuthentication: Map<String, String>
): Map<String, List<String>> {
    if (currentAuthentication.isEmpty()) return existing
    val replacedNames = currentAuthentication.keys.mapTo(mutableSetOf()) { it.lowercase() }
    return buildMap {
        existing.forEach { (name, values) ->
            if (name.lowercase() !in replacedNames) put(name, values)
        }
        currentAuthentication.forEach { (name, value) -> put(name, listOf(value)) }
    }
}
