package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyPluralInfoCloneTest {
    private class Derived : CurrencyPluralInfo(ULocale("en")) { val extra = Any() }
    @Test fun clonePreservesSubclassAndCopiesItsPatternMap() {
        val info = Derived(); val copy = info.clone()
        assertTrue(copy is Derived); assertNotSame(info, copy); assertSame(info.extra, copy.extra)
        assertSame(info.getPluralRules(), copy.getPluralRules())
        copy.setCurrencyPluralPattern("one", "custom")
        assertNotEquals(info.getCurrencyPluralPattern("one"), copy.getCurrencyPluralPattern("one"))
    }
}
