package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICUConfigTest {
    @Test fun pinnedDefaultsAndFallbackPreserveEmptyAndWhitespaceValues() {
        assertEquals("/com/ibm/icu/ICUConfig.properties", ICUConfig.CONFIG_PROPS_FILE)
        for ((name, value) in ICUConfigDefaults.properties) {
            val host = getICUConfigHostProperty(name)
            assertEquals(if (host.isNullOrEmpty()) value else host, ICUConfig.get(name, "caller-default"))
        }
        val key = "icu4k.test.config.unknown"
        val host = getICUConfigHostProperty(key)
        if (host.isNullOrEmpty()) {
            assertNull(ICUConfig.get(key)); assertEquals("caller", ICUConfig.get(key, "caller"))
        }
        assertFailsWith<IllegalArgumentException> { ICUConfig.get("") }
    }
}
