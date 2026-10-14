package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleServiceCoreOracleTest {
    @Test fun changingDefaultFallbackInvalidatesCachedDescriptorsLikeSource() {
        val saved = ULocale.getDefault()
        val sourceSaved = com.ibm.icu.util.ULocale.getDefault()
        val j = com.ibm.icu.impl.ICULocaleService("test"); val k = LocaleServiceCore("test")
        for (id in listOf("en", "fr", "de", "")) { j.registerObject("value:$id", id); k.registerObject("value:$id", id) }
        try {
            for (fallback in listOf("en_US", "fr_FR", "de_DE@currency=EUR", "en_US", "root")) {
                com.ibm.icu.util.ULocale.setDefault(com.ibm.icu.util.ULocale(fallback)); ULocale.setDefault(ULocale(fallback))
                assertEquals(j.validateFallbackLocale(), k.validateFallbackLocale())
                for (id in listOf("zz_ZZ", "en_US_POSIX", "fr_CA@calendar=gregorian", "root", "de_DE@currency=USD")) for (kind in listOf(-1, 0, 4, Int.MIN_VALUE)) {
                    val a = arrayOf<com.ibm.icu.util.ULocale?>(com.ibm.icu.util.ULocale("unchanged"))
                    val b = arrayOf<ULocale?>(ULocale("unchanged"))
                    assertEquals(j.get(com.ibm.icu.util.ULocale(id), kind, a), k.get(ULocale(id), kind, b), "$fallback/$id/$kind")
                    assertEquals(a[0]?.name, b[0]?.getName())
                    val jk = j.createKey(id, kind); val kk = k.createKey(id, kind)
                    assertEquals(jk.canonicalID(), kk!!.canonicalID()); assertEquals(jk.currentDescriptor(), kk.currentDescriptor())
                }
            }
        } finally { com.ibm.icu.util.ULocale.setDefault(sourceSaved); ULocale.setDefault(saved) }
    }
}
