package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.LocaleReadOnlyMap
import kotlin.test.*

class LocaleFallbackTest {
    @Test fun parentSelectionPreservesExplicitParentsScriptsAndVariants() {
        val normal = ICUResourceBundle.OpenType.LOCALE_DEFAULT_ROOT
        val plain = ICUResourceBundle.OpenType.LOCALE_ROOT
        fun parent(name: String, original: String = name, mode: ICUResourceBundle.OpenType = normal) =
            ICUResourceBundle.getParentLocaleID(name, original, mode)
        assertEquals("es_419", parent("es_AR"))
        assertEquals("es_Latn", parent("es_AR", mode = plain))
        assertEquals("zh_TW", parent("zh_Hant_TW"))
        assertEquals("zh_Hant", parent("zh_TW", "zh_Hant_TW"))
        assertNull(parent("zh_Hant")); assertEquals("zh", parent("zh_Hant", mode = plain))
        assertEquals("sr_Latn", parent("sr_Latn_RS")); assertNull(parent("sr_Latn"))
        assertEquals("en_US", parent("en_US_POSIX")); assertEquals("en_", parent("en__"))
        assertEquals("zz_Latn", parent("zz_ZZ")); assertNull(parent("zz"))
        assertEquals(1066, LocaleFallbackData.DEFAULT_SCRIPT_TABLE.size)
        assertEquals(199, LocaleFallbackData.PARENT_LOCALE_TABLE.size)
    }
    @Test fun unmodifiableMapsRejectMutationThroughAllViews() {
        val backing = linkedMapOf("a" to "one")
        val map = LocaleReadOnlyMap(backing)
        assertEquals<Map<String, String>>(backing, map)
        assertSame(map.keys, map.keys); assertSame(map.values, map.values); assertSame(map.entries, map.entries)
        assertFailsWith<UnsupportedOperationException> { map["b"] = "two" }
        assertFailsWith<UnsupportedOperationException> { map.remove("missing") }
        assertFailsWith<UnsupportedOperationException> { map.putAll(emptyMap()) }
        assertFailsWith<UnsupportedOperationException> { map.clear() }
        assertFailsWith<UnsupportedOperationException> { map.entries.first().setValue("changed") }
        for (view in listOf<MutableCollection<*>>(map.keys, map.values, map.entries)) {
            assertFailsWith<UnsupportedOperationException> { view.clear() }
            assertFailsWith<UnsupportedOperationException> { view.removeAll(emptyList()) }
            assertFailsWith<UnsupportedOperationException> { view.retainAll(emptyList()) }
            val iterator = view.iterator()
            assertFailsWith<UnsupportedOperationException> { iterator.remove() }
            iterator.next(); assertFailsWith<UnsupportedOperationException> { iterator.remove() }
        }
        backing["b"] = "two"
        assertEquals(2, map.size); assertEquals(setOf("a", "b"), map.keys)
        assertEquals("two", map["b"])
        assertEquals(backing.entries, map.entries)
        assertEquals(backing.hashCode(), map.hashCode())
    }
}
