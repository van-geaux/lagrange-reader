package com.vangeaux.lagrange.provider

import com.vangeaux.lagrange.*

import android.content.Context
import com.vangeaux.lagrange.provider.komga.KomgaRepository


class ProviderRepositoryResolver(private val context: Context) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun resolve(
        _serverUrl: String,
        providerId: String,
        fallback: BookOrbitDataSource
    ): BookOrbitDataSource {
        return when (providerId) {
            PROVIDER_KOMGA -> KomgaRepository(context)
            PROVIDER_BOOKORBIT -> BookOrbitRepository(context)
            else -> fallback
        }
    }
}
