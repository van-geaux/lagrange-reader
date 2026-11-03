package com.vangeaux.lagrange.provider.bookorbit

/** Authenticated JSON transport supplied by the BookOrbit compatibility repository. */
internal interface BookOrbitAuthenticatedJsonRequestExecutor {
    suspend fun executeAuthenticatedJsonRequest(path: String, method: String, jsonBody: String?): String
}
