package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleUiListOracleTest {
    @Test fun groupedUiRowsNamesAndComparatorsMatch() {
        val sets = listOf(emptyList(), listOf("en"), listOf("root", "en", "en_US"), listOf("en_US", "en_GB", "en_AU"), listOf("sr", "sr_Latn", "sr_Cyrl_RS"), listOf("zh", "zh_Hant", "zh_Hant_HK"), listOf("en_Cyrl_US", "en_Cyrl_GB"), listOf("fr", "ja", "ar", "de"), listOf("en_US_POSIX", "en@calendar=buddhist", "en@currency=USD"), listOf("zz", "zz_Latn_XX"), listOf("en@foo=bar"), listOf("eo_001", "eo_US"))
        val strings = Comparator<Any?> { a, b -> (a as String).compareTo(b as String) }
        for (id in listOf("en", "fr", "ja", "ar")) for (cap in listOf(DisplayContext.CAPITALIZATION_NONE, DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)) for (self in listOf(false, true)) {
            val j = com.ibm.icu.impl.LocaleDisplayNamesImpl(com.ibm.icu.util.ULocale(id), com.ibm.icu.text.DisplayContext.valueOf(cap.name), com.ibm.icu.text.DisplayContext.LENGTH_SHORT, com.ibm.icu.text.DisplayContext.NO_SUBSTITUTE)
            val k = LocaleDisplayNameComponents(ULocale(id), cap, DisplayContext.LENGTH_SHORT, DisplayContext.NO_SUBSTITUTE)
            for (set in sets) {
                val a = j.getUiListCompareWholeItems(set.map { com.ibm.icu.util.ULocale(it) }.toSet(), com.ibm.icu.text.LocaleDisplayNames.UiListItem.getComparator(strings, self))
                val b = k.getUiListCompareWholeItems(set.map(::ULocale).toSet(), LocaleUiListItem.getComparator(strings, self))
                assertEquals(a.map { listOf(it.minimized.name, it.modified.name, it.nameInDisplayLocale, it.nameInSelf) }, b.map { listOf(it.minimized!!.getName(), it.modified!!.getName(), it.nameInDisplayLocale, it.nameInSelf) }, "$id/$cap/$self/$set")
            }
        }
    }
    @Test fun itemEqualityHashTextAndMalformedLocalesMatch() {
        val j = com.ibm.icu.text.LocaleDisplayNames.UiListItem(com.ibm.icu.util.ULocale("en"), com.ibm.icu.util.ULocale("en_GB"), "English", "self")
        val k = LocaleUiListItem(ULocale("en"), ULocale("en_GB"), "English", "self")
        assertEquals(j.hashCode(), k.hashCode()); assertEquals(j.toString(), k.toString())
        fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { e.javaClass.simpleName }
        val jc = com.ibm.icu.impl.LocaleDisplayNamesImpl(com.ibm.icu.util.ULocale("en"), com.ibm.icu.text.LocaleDisplayNames.DialectHandling.STANDARD_NAMES)
        val kc = LocaleDisplayNameComponents(ULocale("en"))
        for (id in listOf("en__abc", "en__a", "en_US_POSIX")) {
            assertEquals(outcome { jc.getUiListCompareWholeItems(setOf(com.ibm.icu.util.ULocale(id)), null).map { it.toString() } }, outcome { kc.getUiListCompareWholeItems(setOf(ULocale(id)), null).map { it.toString() } })
        }
    }
    @Test fun nullableItemFieldsAndComparatorShortCircuitMatchSource() {
        fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { e.javaClass.simpleName }
        val strings = Comparator<Any?> { a, b -> (a?.toString() ?: "").compareTo(b?.toString() ?: "") }
        for (mask in 0..15) {
            val jm = if (mask and 1 == 0) com.ibm.icu.util.ULocale("en") else null
            val jx = if (mask and 2 == 0) com.ibm.icu.util.ULocale("en_US") else null
            val km = if (mask and 1 == 0) ULocale("en") else null
            val kx = if (mask and 2 == 0) ULocale("en_US") else null
            val display = if (mask and 4 == 0) "display" else null
            val self = if (mask and 8 == 0) "self" else null
            val a = com.ibm.icu.text.LocaleDisplayNames.UiListItem(jm, jx, display, self)
            val b = com.ibm.icu.text.LocaleDisplayNames.UiListItem(jm, jx, display, self)
            val x = LocaleUiListItem(km, kx, display, self)
            val y = LocaleUiListItem(km, kx, display, self)
            assertEquals(outcome { a == b }, outcome { x == y }, "$mask/equals")
            assertEquals(outcome { a.hashCode() }, outcome { x.hashCode() }, "$mask/hash")
            assertEquals(a.toString(), x.toString()); assertTrue(a == a); assertTrue(x == x)
            for (inSelf in listOf(false, true)) {
                assertEquals(outcome { com.ibm.icu.text.LocaleDisplayNames.UiListItem.getComparator(strings, inSelf).compare(a, b) }, outcome { LocaleUiListItem.getComparator(strings, inSelf).compare(x, y) }, "$mask/$inSelf")
            }
        }
    }

}
