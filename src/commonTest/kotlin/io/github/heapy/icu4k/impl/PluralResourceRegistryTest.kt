package io.github.heapy.icu4k.impl

import kotlin.test.*

class PluralResourceRegistryTest {
    @Test fun embeddedPluralDataIsAdvertisedAndEnumerated() {
        val names = mutableListOf<String>()
        ICUData.visitResources(ICUData.ICU_BASE_NAME + "/") { names.add(it) }
        for (name in listOf("plurals.res", "pluralRanges.res")) {
            assertTrue(ICUData.exists(name))
            assertTrue(ICUData.exists("/" + ICUData.ICU_BASE_NAME + "/" + name))
            assertNotNull(ICUData.getBytes(name))
            assertTrue(name in names)
        }
    }
}
