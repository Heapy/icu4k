package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICUConfigNodeTest {
    @Test fun nodeOverridesAreReadEachTimeAndEmptyFallsBack() {
        val environment: dynamic = js("process.env")
        val key = "com.ibm.icu.impl.ICUResourceBundle.skipRuntimeLocaleResourceScan"
        val saved: dynamic = environment[key]
        try {
            environment[key] = "true"; assertEquals("true", ICUConfig.get(key))
            assertEquals("caller", ICUConfig.get(key + "\u0000suffix", "caller"))
            environment[key] = ""; assertEquals("false", ICUConfig.get(key))
            environment[key] = " "; assertEquals(" ", ICUConfig.get(key))
        } finally {
            if (jsTypeOf(saved) == "undefined") js("delete environment[key]") else environment[key] = saved
        }
    }
}
