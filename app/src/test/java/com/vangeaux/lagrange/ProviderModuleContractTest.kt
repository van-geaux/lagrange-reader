package com.vangeaux.lagrange

import com.vangeaux.lagrange.core.ProviderModules
import com.vangeaux.lagrange.core.ProviderFeatureAvailability
import com.vangeaux.lagrange.provider.kavita.kavitaFeatureAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModuleContractTest {
    @Test
    fun providerCompositionHasIndependentFeatureSlots() {
        val fields = ProviderModules::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.containsAll(setOf("login", "libraries", "bookCatalog", "reader", "downloads", "epubBookmarks")))
        assertTrue(fields.size >= 20)
    }

    @Test
    fun seriesCatalogAvailabilityIsIndependentFromHomeCatalogLoading() {
        assertTrue(ProviderFeatureAvailability().seriesCatalog)
        assertFalse(ProviderFeatureAvailability(seriesCatalog = false).seriesCatalog)
    }

    @Test
    fun libraryBookCatalogAvailabilityIsIndependentFromHomeCatalogLoading() {
        assertTrue(ProviderFeatureAvailability().libraryBookCatalog)
        assertFalse(ProviderFeatureAvailability(libraryBookCatalog = false).libraryBookCatalog)
    }

    @Test
    fun kavitaAdvertisesImplementedCatalogCapabilities() {
        val availability = kavitaFeatureAvailability()

        assertTrue(availability.libraryBookCatalog)
        assertTrue(availability.seriesCatalog)
    }
}
