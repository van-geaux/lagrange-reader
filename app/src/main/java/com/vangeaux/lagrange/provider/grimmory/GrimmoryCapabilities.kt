package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.UserFacingReason
import com.vangeaux.lagrange.core.ProviderFeatureAvailability

internal fun grimmoryFeatureAvailability() = ProviderFeatureAvailability(
    serverHostedSignIn = false,
    libraryBookCatalog = true,
    seriesCatalog = false,
    achievements = false,
    statistics = false,
    smartScopes = false,
    collections = false,
    authors = false,
    userRating = false,
    extendedReadingStatuses = true,
    readingStatusOptions = listOf(
        com.vangeaux.lagrange.BookReadStatus.UNREAD,
        com.vangeaux.lagrange.BookReadStatus.READING,
        com.vangeaux.lagrange.BookReadStatus.REREADING,
        com.vangeaux.lagrange.BookReadStatus.READ,
        com.vangeaux.lagrange.BookReadStatus.ABANDONED
    )
)

internal fun grimmoryUnavailable(feature: String): Nothing = throw UserFacingException(
    "$feature is not available in this Grimmory integration yet.",
    UserFacingReason.CAPABILITY_UNAVAILABLE
)
