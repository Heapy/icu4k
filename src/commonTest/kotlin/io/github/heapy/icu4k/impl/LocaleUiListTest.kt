package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleUiListTest {
    private val comparator = LocaleUiListItem.getComparator(Comparator { a, b -> (a as String).compareTo(b as String) }, false)
    @Test fun minimizesDuplicatesAndKeepsDisambiguatingRegions() {
        val display = LocaleDisplayNameComponents(ULocale("en"))
        val rows = display.getUiListCompareWholeItems(setOf(ULocale("en"), ULocale("en_US"), ULocale("en_GB")), comparator)
        assertEquals(2, rows.size)
        assertEquals(setOf("en_US", "en_GB"), rows.map { it.modified!!.getName() }.toSet())
        rows.clear(); assertTrue(rows.isEmpty())
        val french = LocaleDisplayNameComponents(ULocale("fr"), DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU)
        assertEquals("Anglais", french.getUiListCompareWholeItems(setOf(ULocale("en")), comparator).single().nameInDisplayLocale)
    }
    @Test fun comparatorTieBreakAndValueSemantics() {
        val a = LocaleUiListItem(ULocale("en"), ULocale("en_GB"), "same", "self")
        val b = LocaleUiListItem(ULocale("en"), ULocale("en_US"), "same", "self")
        assertTrue(comparator.compare(a, b) < 0)
        assertEquals(a, LocaleUiListItem(ULocale("en"), ULocale("en_GB"), "same", "self"))
        assertEquals(ULocale("en_GB").hashCode() xor "same".hashCode(), a.hashCode())
        val display = LocaleDisplayNameComponents(ULocale("en"))
        assertTrue(display.getUiListCompareWholeItems(emptySet(), null).isEmpty())
        assertFailsWith<ClassCastException> { display.getUiListCompareWholeItems(setOf(ULocale("en"), ULocale("fr")), null) }
    }
}
