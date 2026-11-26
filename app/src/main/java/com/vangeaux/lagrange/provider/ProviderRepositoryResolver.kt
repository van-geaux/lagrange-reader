package com.vangeaux.lagrange.provider

import com.vangeaux.lagrange.*

import android.content.Context
import com.vangeaux.lagrange.PROVIDER_KAVITA
import com.vangeaux.lagrange.provider.komga.KomgaRepository
import com.vangeaux.lagrange.provider.kavita.KavitaRepository
import com.vangeaux.lagrange.provider.grimmory.GrimmoryRepository
import com.vangeaux.lagrange.provider.opds.OpdsRepository


class ProviderRepositoryResolver(private val context: Context) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun resolve(
        _serverUrl: String,
        providerId: String,
        fallback: BookOrbitDataSource
    ): BookOrbitDataSource {
        return when (providerId) {
            PROVIDER_KOMGA -> KomgaRepository(context)
            PROVIDER_KAVITA -> KavitaRepository(context)
            PROVIDER_GRIMMORY -> GrimmoryRepository(context)
            PROVIDER_OPDS -> OpdsRepository(context, _serverUrl)
            PROVIDER_CALIBRE_WEB_AUTOMATED -> {
                val profiles = ServerProfileStore(context)
                val legacyProfile = profiles.active()
                    ?.takeIf { it.providerId == PROVIDER_CALIBRE_WEB_AUTOMATED && serverUrlsMatch(it.serverUrl, _serverUrl) }
                    ?: profiles.readAll().firstOrNull {
                        it.providerId == PROVIDER_CALIBRE_WEB_AUTOMATED && serverUrlsMatch(it.serverUrl, _serverUrl)
                    }
                OpdsRepository(context, _serverUrl, legacyCwaProfileId = legacyProfile?.id, legacyCwa = true)
            }
            PROVIDER_BOOKORBIT -> BookOrbitRepository(context)
            else -> fallback
        }
    }
}
