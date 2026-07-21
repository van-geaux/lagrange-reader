package com.bookorbit.android

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.readium.r2.shared.util.AbsoluteUrl

@UnstableApi
internal class AuthenticatedMedia3HttpDataSourceFactory(
    private val headersProvider: suspend (AbsoluteUrl) -> Map<String, String>,
    private val recoverAuthentication: suspend () -> Boolean
) : DataSource.Factory {
    override fun createDataSource(): DataSource = AuthenticatedMedia3HttpDataSource(
        headersProvider = headersProvider,
        recoverAuthentication = recoverAuthentication
    )
}

@UnstableApi
internal class AuthenticatedMedia3HttpDataSource(
    private val headersProvider: suspend (AbsoluteUrl) -> Map<String, String>,
    private val recoverAuthentication: suspend () -> Boolean
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: HttpDataSource? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        var authenticationRecovered = false
        while (true) {
            val next = createDelegate(dataSpec.uri)
            delegate = next
            try {
                return next.open(dataSpec)
            } catch (error: HttpDataSource.InvalidResponseCodeException) {
                runCatching { next.close() }
                val canRetry = !authenticationRecovered && error.responseCode in setOf(401, 403)
                if (!canRetry || !runBlocking { recoverAuthentication() }) throw error
                authenticationRecovered = true
            }
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(delegate) { "The audiobook data source is not open." }
            .read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders.orEmpty()

    override fun close() {
        delegate?.close()
        delegate = null
    }

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    private fun createDelegate(uri: Uri): HttpDataSource {
        val absoluteUrl = AbsoluteUrl(uri.toString())
            ?: throw IOException("The audiobook stream URL is invalid.")
        val headers = runBlocking { headersProvider(absoluteUrl) }
        return DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .createDataSource()
            .also { dataSource ->
                transferListeners.forEach(dataSource::addTransferListener)
            }
    }
}
