package com.vangeaux.lagrange.provider.bookorbit

import com.vangeaux.lagrange.BookOrbitDataSource
import com.vangeaux.lagrange.BookOrbitOidcCallback
import com.vangeaux.lagrange.BookOrbitOidcProvider
import com.vangeaux.lagrange.BookOrbitOidcState
import com.vangeaux.lagrange.BookOrbitOidcTransaction

internal interface BookOrbitOidcModule {
    suspend fun loadProviders(): List<BookOrbitOidcProvider>
    suspend fun requestState(providerSlug: String): BookOrbitOidcState
    suspend fun exchangeCallback(transaction: BookOrbitOidcTransaction, callback: BookOrbitOidcCallback)
}

internal class BookOrbitOidcModuleImpl(
    private val repository: BookOrbitDataSource
) : BookOrbitOidcModule {
    override suspend fun loadProviders(): List<BookOrbitOidcProvider> = repository.loadOidcProviders()

    override suspend fun requestState(providerSlug: String): BookOrbitOidcState =
        repository.requestOidcState(providerSlug)

    override suspend fun exchangeCallback(
        transaction: BookOrbitOidcTransaction,
        callback: BookOrbitOidcCallback
    ) {
        repository.exchangeOidcCallback(transaction, callback)
    }
}
