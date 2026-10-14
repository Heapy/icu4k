package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.locale.LocaleReadOnlyList
import kotlin.test.*

class ULocaleAvailabilityTest {
    @Test fun publicListsCopyArraysCombineInOrderAndRejectMutation() {
        val original = ULocale.getAvailableLocales()
        val saved = original[0]
        original[0] = ULocale("zz")
        assertEquals(saved, ULocale.getAvailableLocales()[0])
        val defaults = ULocale.getAvailableLocalesByType(ULocale.AvailableType.DEFAULT)
        val aliases = ULocale.getAvailableLocalesByType(ULocale.AvailableType.ONLY_LEGACY_ALIASES)
        val combined = ULocale.getAvailableLocalesByType(ULocale.AvailableType.WITH_LEGACY_ALIASES)
        assertEquals(defaults.toList() + aliases.toList(), combined.toList())
        assertFailsWith<IllegalArgumentException> { ULocale.getAvailableLocalesByType(null) }
        assertFailsWith<UnsupportedOperationException> { (defaults as MutableCollection<ULocale>).clear() }
        assertFailsWith<UnsupportedOperationException> { (combined as MutableList<ULocale>)[0] = saved }
        assertEquals(ICUResourceBundle.getAvailableULocales()!!.map { it.getName() }, defaults.map { it.getName() })
    }
    @Test fun commonReadOnlyListRetainsViewsAndRejectsIteratorMutationImmediately() {
        val array = arrayOf("a", "b", "c")
        val list = LocaleReadOnlyList(array.asList())
        val sub = list.subList(1, 3)
        array[1] = "changed"
        assertEquals("changed", sub[0])
        assertFailsWith<UnsupportedOperationException> { list.addAll(emptyList()) }
        assertFailsWith<UnsupportedOperationException> { list.remove("missing") }
        assertFailsWith<UnsupportedOperationException> { sub.clear() }
        val iterator = list.listIterator(1)
        assertEquals(1, iterator.nextIndex()); assertEquals(0, iterator.previousIndex())
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
        assertFailsWith<UnsupportedOperationException> { iterator.set("x") }
        assertFailsWith<UnsupportedOperationException> { iterator.add("x") }
        assertEquals("a", iterator.previous()); assertEquals("a", iterator.next())
    }
}
