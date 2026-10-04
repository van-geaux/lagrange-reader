package com.vangeaux.lagrange

import com.vangeaux.lagrange.core.ProviderModules
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModuleContractTest {
    @Test
    fun providerCompositionHasIndependentFeatureSlots() {
        val fields = ProviderModules::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.containsAll(setOf("login", "libraries", "bookCatalog", "reader", "downloads")))
        assertTrue(fields.size >= 20)
    }
}
