package com.vangeaux.lagrange.core

import android.content.Context

/** Provider-neutral EPUB text-to-speech capability. */
interface EpubTtsModule {
    fun currentAccountEpoch(): Long
    fun isCurrentAccount(accountEpoch: Long): Boolean
    fun beginRequest(): Long
    fun isCurrentRequest(requestId: Long): Boolean
    fun cancelRequest(requestId: Long)
    fun invalidateAccount()
    fun start(
        context: Context,
        ownerToken: String,
        requestId: Long,
        accountEpoch: Long,
        readerKey: String,
        title: String
    )
    fun stop(context: Context, ownerToken: String, requestId: Long? = null)
    suspend fun stopAndAwait(context: Context)
}
