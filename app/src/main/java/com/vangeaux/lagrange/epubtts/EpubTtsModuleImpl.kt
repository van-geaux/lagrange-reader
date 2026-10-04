package com.vangeaux.lagrange.epubtts

import android.content.Context
import com.vangeaux.lagrange.EpubTtsAccountSession
import com.vangeaux.lagrange.EpubTtsPlaybackService
import com.vangeaux.lagrange.EpubTtsRequestSession
import com.vangeaux.lagrange.core.EpubTtsModule

/** Application implementation of the provider-neutral EPUB TTS capability. */
object EpubTtsModuleImpl : EpubTtsModule {
    override fun currentAccountEpoch(): Long = EpubTtsAccountSession.currentEpoch()

    override fun isCurrentAccount(accountEpoch: Long): Boolean =
        EpubTtsAccountSession.isCurrent(accountEpoch)

    override fun beginRequest(): Long = EpubTtsRequestSession.begin()

    override fun isCurrentRequest(requestId: Long): Boolean =
        EpubTtsRequestSession.isCurrent(requestId)

    override fun cancelRequest(requestId: Long) {
        EpubTtsRequestSession.cancel(requestId)
    }

    override fun invalidateAccount() {
        EpubTtsAccountSession.invalidate()
    }

    override fun start(
        context: Context,
        ownerToken: String,
        requestId: Long,
        accountEpoch: Long,
        readerKey: String,
        title: String
    ) {
        EpubTtsPlaybackService.start(
            context = context,
            ownerToken = ownerToken,
            requestId = requestId,
            accountEpoch = accountEpoch,
            readerKey = readerKey,
            title = title
        )
    }

    override fun stop(context: Context, ownerToken: String, requestId: Long?) {
        EpubTtsPlaybackService.stop(context, ownerToken, requestId)
    }

    override suspend fun stopAndAwait(context: Context) {
        EpubTtsPlaybackService.stopAndAwait(context)
    }
}
