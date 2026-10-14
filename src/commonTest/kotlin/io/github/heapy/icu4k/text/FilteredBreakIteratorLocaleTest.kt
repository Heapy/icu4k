package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class FilteredBreakIteratorLocaleTest {
    @Test fun localeAbbreviationsAreLoadedAndCanBeRemoved() {
        val builder = FilteredBreakIteratorBuilder.getInstance(ULocale("en_US"))
        assertFalse(builder.suppressBreakAfter("Mr."))
        assertTrue(builder.unsuppressBreakAfter("Mr."))
        assertTrue(builder.suppressBreakAfter("Mr."))
        val iterator = builder.wrapIteratorWithFilter(RuleBasedBreakIterator.getInstanceFromCompiledRules(ICUBinary.getRequiredData("brkitr/sent.brk")))
        iterator.setText("Mr. Smith went home. Next.")
        assertEquals(21, iterator.next()); assertEquals(26, iterator.next())
        for (id in listOf("en", "fr", "de", "es")) assertTrue(ICUData.exists("${ICUData.ICU_BRKITR_BASE_NAME}/$id.res"))
    }
}
