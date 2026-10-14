package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import com.ibm.icu.util.ULocale as JLocale

class ServiceDisplayNamesOracleTest {
    @Test fun displayEnumerationCacheFilteringAndInvalidationMatchSource() {
        val j = com.ibm.icu.impl.ICULocaleService(); val k = ICULocaleService()
        for (id in listOf("en", "en_US", "fr", "de", "zh_Hant", "sr_Latn")) {
            j.registerObject(id, JLocale(id)); k.registerObject(id, ULocale(id))
        }
        val comparator = Comparator<Any?> { a, b -> (b as String).compareTo(a as String) }
        for (display in listOf("en", "fr", "ar")) for (cmp in listOf(null, comparator)) {
            val jm = j.getDisplayNames(JLocale(display), cmp)
            val km = k.getDisplayNames(ULocale(display), cmp)
            assertEquals(jm.entries.toList(), km.entries.toList())
            assertSame(km, k.getDisplayNames(ULocale(display), cmp))
            for (match in listOf("en", "en_US", "fr", "unknown")) {
                val jf = j.getDisplayNames(JLocale(display), cmp, match)
                val kf = k.getDisplayNames(ULocale(display), cmp, match)
                assertEquals(jf.entries.toList(), kf.entries.toList())
                assertSame<Any?>(jf.comparator(), kf.comparator())
                jf["extra"] = "new"; kf["extra"] = "new"
                assertEquals(jf, kf)
            }
        }
        j.reset(); k.reset()
        assertEquals(j.getDisplayNames(JLocale.ENGLISH), k.getDisplayNames(ULocale.ENGLISH))
    }
    @Test fun portableSortedMapMatchesTreeMapForMixedMutationsAndRanges() {
        val comparator = Comparator<Any?> { a, b -> (a as String).lowercase().compareTo((b as String).lowercase()) }
        val j = java.util.TreeMap<String?, String>(comparator)
        val k = PortableServiceDisplayNamesMap(comparator)
        val random = kotlin.random.Random(154)
        repeat(500) {
            val key = ('a'.code + random.nextInt(12)).toChar().toString().let { if (random.nextBoolean()) it.uppercase() else it }
            if (random.nextBoolean()) assertEquals(j.put(key, "$it"), k.put(key, "$it")) else assertEquals(j.remove(key), k.remove(key))
            assertEquals(j.entries.toList(), k.entries.toList())
            assertEquals(j.subMap("c", "k").entries.toList(), k.subMap("c", "k").entries.toList())
            assertEquals(j.keys.contains(key.uppercase()), k.keys.contains(key.uppercase()))
            val probe = java.util.AbstractMap.SimpleEntry<String?, String>(key.uppercase(), j[key] ?: "missing")
            assertEquals(j.entries.contains(probe), k.entries.contains(probe))
            if (it % 13 == 0) assertEquals(j.keys.remove(key.uppercase()), k.keys.remove(key.uppercase()))
        }
    }
    @Test fun nullLocaleCacheAndNullDisplayNameMatchSource() {
        val j = com.ibm.icu.impl.ICUService(); val k = ICUService()
        j.registerObject("x", "id"); k.registerObject("x", "id")
        assertEquals(j.getDisplayNames(null as JLocale?), k.getDisplayNames(null as ULocale?))
        assertFailsWith<NullPointerException> { j.getDisplayNames(null as JLocale?) }
        assertFailsWith<NullPointerException> { k.getDisplayNames(null as ULocale?) }
    }
    @Test fun equalComparatorsReuseCacheAndPreserveOriginalComparatorInCopies() {
        class EqualComparator : Comparator<Any?> {
            override fun compare(a: Any?, b: Any?): Int = (a as String).compareTo(b as String)
            override fun equals(other: Any?): Boolean = other is EqualComparator
            override fun hashCode(): Int = 1
        }
        val j = com.ibm.icu.impl.ICUService(); val k = ICUService()
        j.registerObject("x", "en"); k.registerObject("x", "en")
        val first = EqualComparator(); val equal = EqualComparator()
        val jm = j.getDisplayNames(JLocale.ENGLISH, first)
        val km = k.getDisplayNames(ULocale.ENGLISH, first)
        assertSame(jm, j.getDisplayNames(JLocale.ENGLISH, equal))
        assertSame(km, k.getDisplayNames(ULocale.ENGLISH, equal))
        assertSame<Any?>(first, j.getDisplayNames(JLocale.ENGLISH, equal, "en").comparator())
        assertSame<Any?>(first, k.getDisplayNames(ULocale.ENGLISH, equal, "en").comparator())
    }
    @Test fun comparatorCanAcceptNullDisplayNames() {
        val j = com.ibm.icu.impl.ICUService(); val k = ICUService()
        j.registerFactory(object : com.ibm.icu.impl.ICUService.Factory {
            override fun create(key: com.ibm.icu.impl.ICUService.Key?, service: com.ibm.icu.impl.ICUService?): Any? = null
            override fun updateVisibleIDs(result: MutableMap<String, com.ibm.icu.impl.ICUService.Factory>) { result["id"] = this }
            override fun getDisplayName(id: String?, locale: JLocale?): String? = null
        })
        k.registerFactory(object : ICUService.Factory {
            override fun create(key: ICUService.Key?, service: ICUService?): Any? = null
            override fun updateVisibleIDs(result: MutableMap<String, ICUService.Factory>) { result["id"] = this }
            override fun getDisplayName(id: String?, locale: ULocale?): String? = null
        })
        assertFailsWith<NullPointerException> { j.getDisplayNames(JLocale.ENGLISH) }
        assertFailsWith<NullPointerException> { k.getDisplayNames(ULocale.ENGLISH) }
        val cmp = Comparator<Any?> { a, b -> compareValues(a as String?, b as String?) }
        assertEquals(j.getDisplayNames(JLocale.ENGLISH, cmp), k.getDisplayNames(ULocale.ENGLISH, cmp))
        val portable = PortableServiceDisplayNamesMap(cmp)
        portable[null] = "id"
        assertEquals(j.getDisplayNames(JLocale.ENGLISH, cmp), portable)
    }

}
