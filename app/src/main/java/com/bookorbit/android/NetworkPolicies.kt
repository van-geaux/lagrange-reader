package com.bookorbit.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal enum class CellularDownloadDecision {
    START,
    ASK,
    BLOCK
}

internal fun cellularDownloadDecision(
    policy: CellularDownloadPolicy,
    isCellularOrMetered: Boolean
): CellularDownloadDecision {
    if (!isCellularOrMetered) return CellularDownloadDecision.START
    return when (policy) {
        CellularDownloadPolicy.ALWAYS -> CellularDownloadDecision.START
        CellularDownloadPolicy.NEVER -> CellularDownloadDecision.BLOCK
        CellularDownloadPolicy.ASK_FOR_CONFIRMATION -> CellularDownloadDecision.ASK
    }
}

internal fun Context.isActiveCellularOrMeteredNetwork(): Boolean {
    val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = connectivity.activeNetwork ?: return false
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
        (connectivity.isActiveNetworkMetered &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
}
