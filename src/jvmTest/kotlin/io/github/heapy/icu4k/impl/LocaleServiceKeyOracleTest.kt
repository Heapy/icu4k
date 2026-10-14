package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import com.ibm.icu.impl.ICULocaleService.LocaleKey as Original

class LocaleServiceKeyOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { e.javaClass.simpleName }
    private fun compare(j: Original?, k: LocaleServiceKey?) {
        if (j == null) { assertNull(k); return }
        val key = k!!
        assertEquals(j.id(), key.id()); assertEquals(j.canonicalID(), key.canonicalID())
        assertEquals(j.kind(), key.kind()); assertEquals(j.prefix(), key.prefix())
        assertEquals(j.canonicalLocale().name, key.canonicalLocale().getName())
        for (child in listOf("", "en", "en_US", "english", "root", "de_DE_POSIX", j.canonicalID(), j.canonicalID() + "_X")) assertEquals(j.isFallbackOf(child), key.isFallbackOf(child))
        var steps = 0
        while (true) {
            assertEquals(j.currentID(), key.currentID()); assertEquals(j.currentDescriptor(), key.currentDescriptor())
            assertEquals(outcome { j.currentLocale().name }, outcome { key.currentLocale().getName() })
            val more = j.fallback(); assertEquals(more, key.fallback())
            if (!more) break
            assertTrue(steps++ < 30)
        }
        assertEquals(j.currentDescriptor(), key.currentDescriptor())
        assertEquals(outcome { j.currentLocale().name }, outcome { key.currentLocale().getName() })
        assertEquals(outcome { j.fallback() }, outcome { key.fallback() })
    }
    @Test fun canonicalFactoriesAndFallbackChainsMatch() {
        val ids = listOf(null, "", "root", "ROOT", "root@currency=EUR", "en-US", "DE_de_POSIX", "en___POSIX", "_US", "zh_Hant_TW@calendar=roc", "de_DE@currency=EUR;collation=phonebook", "und", "i-klingon", "en_US_POSIX")
        for (id in ids) for (fallback in listOf(null, "", "en", "en_US", "de_DE", "root", "fr___CA")) for (kind in listOf(-1, 0, 2, -2, Int.MIN_VALUE, Int.MAX_VALUE)) {
            compare(Original.createWithCanonicalFallback(id, fallback, kind), LocaleServiceKey.createWithCanonicalFallback(id, fallback, kind))
            val jlocale = id?.let { com.ibm.icu.util.ULocale(it) }
            val klocale = id?.let(::ULocale)
            compare(Original.createWithCanonical(jlocale, fallback, kind), LocaleServiceKey.createWithCanonical(klocale, fallback, kind))
        }
    }
    @Test fun protectedConstructorAndVirtualDescriptorCallbacksMatch() {
        for (canonical in listOf(null, "root", "ROOT@currency=USD", "en___US@x=y", "")) for (fallback in listOf(null, "", "en_US")) for (kind in listOf(-1, 2)) {
            val j = object : Original("input", canonical, fallback, kind) { }
            val k = object : LocaleServiceKey("input", canonical, fallback, kind) { }
            compare(j, k)
        }
        val j = object : Original("input", "en_US", null, 2) {
            override fun kind(): Int = 42
            override fun currentID(): String = "override"
        }
        val k = object : LocaleServiceKey("input", "en_US", null, 2) {
            override fun kind(): Int = 42
            override fun currentID(): String = "override"
        }
        compare(j, k)
    }

}
