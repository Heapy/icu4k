package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class DateTimePatternDiagnosticsTest {
    @Test fun fieldsKeepQuotedLiteralsAndClassifyTextAndNumbers() {
        val core=DateTimePatternGeneratorCore.empty()
        assertEquals("{Year:N}-{Month:N}-{Day:N}",core.getFields("yyyy-MM-dd"))
        assertEquals("{Weekday:S}, {Month:S} {Day:N}",core.getFields("EEEE, MMM d"))
        assertEquals("'year' {Year:N}",core.getFields("'year' yyyy"))
        assertFailsWith<IndexOutOfBoundsException> { core.getFields("MMMMMM") }
        assertEquals("{Hour:N}:{Minute:N}",core.getFields("HH:mm"))
    }
    @Test fun similarityUsesSortedDistinctTokensAndSkipsEraAndAmPm() {
        val core=DateTimePatternGeneratorCore.empty()
        assertTrue(core.skeletonsAreSimilar("GyyyyMMdda","yMd"))
        assertTrue(core.skeletonsAreSimilar("yyyyMMMMEEE","yME"))
        assertTrue(core.skeletonsAreSimilar("EEEy","cccy"))
        assertFalse(core.skeletonsAreSimilar("EEEM","cccM"))
        assertTrue(core.skeletonsAreSimilar("'literal'","'literal'"))
        assertTrue(core.skeletonsAreSimilar("'x'","'z'"))
        assertFailsWith<IndexOutOfBoundsException> { core.skeletonsAreSimilar("'literal'","'other'") }
        assertTrue(core.skeletonsAreSimilar("Ga",""))
    }
    @Test fun singleFieldUsesCodeUnitsWithoutParsing() {
        assertTrue(DateTimePatternGeneratorCore.isSingleField("????"))
        assertTrue(DateTimePatternGeneratorCore.isSingleField("yyyy"))
        assertFalse(DateTimePatternGeneratorCore.isSingleField("yM"))
        assertFalse(DateTimePatternGeneratorCore.isSingleField("😀"))
        assertFailsWith<IndexOutOfBoundsException> { DateTimePatternGeneratorCore.isSingleField("") }
    }
}
