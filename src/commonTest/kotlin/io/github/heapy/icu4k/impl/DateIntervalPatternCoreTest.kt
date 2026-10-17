package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateIntervalPatternCoreTest {
    private class State(id: String, override var skeleton: String?) : DateIntervalPatternCore.Owner {
        override val locale = ULocale(id)
        override val info = DateIntervalInfo(locale).freeze()
        override val fullPattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton ?: "yMdHm")
        override var datePattern: String? = null
        override var timePattern: String? = null
        override var dateTimeFormat: String? = null
    }
    @Test fun cldrDateAndHourPatternsMatchSourceGoldens() {
        val en = DateIntervalPatternCore(State("en_US", "yMMMd")).initialize()
        assertEquals(listOf("d", "G", "y", "M"), en.keys.toList())
        assertEquals("MMM d\u2009–\u2009", en["d"]!!.getFirstPart())
        assertEquals("d, y", en["d"]!!.getSecondPart())
        val ja = DateIntervalPatternCore(State("ja", "yMMMd")).initialize()
        assertEquals("y年M月d日～", ja["M"]!!.getFirstPart())
        assertEquals("M月d日", ja["M"]!!.getSecondPart())
        val state = State("en_US", "Jm")
        val hours = DateIntervalPatternCore(state).initialize()
        assertEquals(listOf("a", "d", "G", "h", "y", "m", "M"), hours.keys.toList())
        assertEquals("h:mm\u2009–\u2009", hours["h"]!!.getFirstPart())
        assertEquals("h:mm", hours["h"]!!.getSecondPart())
        assertNull(hours["G"]!!.getFirstPart())
        assertEquals("M/d/y G, h:mm\u202fa", hours["G"]!!.getSecondPart())
        assertNull(state.datePattern); assertNull(state.dateTimeFormat)
        assertEquals("h:mm\u202fa", state.timePattern)
    }
    @Test fun combinedDateTimeAndAlternateCalendarPatternsMatchSource() {
        val state = State("fr", "yMdHm")
        val patterns = DateIntervalPatternCore(state).initialize()
        assertEquals(listOf("a", "G", "h", "m"), patterns.keys.toList())
        assertEquals("dd/MM/y, HH:mm\u2009–\u2009", patterns["m"]!!.getFirstPart())
        assertEquals("HH:mm", patterns["m"]!!.getSecondPart())
        assertEquals("dd/MM/y", state.datePattern)
        assertEquals("HH:mm", state.timePattern)
        val chinese = DateIntervalPatternCore(State("zh@calendar=chinese", "yMMMd")).initialize()
        assertEquals(listOf("d", "y", "M"), chinese.keys.toList())
        assertEquals("r年MMMd至", chinese["d"]!!.getFirstPart())
        assertEquals("d", chinese["d"]!!.getSecondPart())
        val derived = State("en_US", null)
        DateIntervalPatternCore(derived).initialize()
        assertEquals("yMdHHmm", derived.skeleton)
    }
    @Test fun normalizationQuotedWidthAdjustmentAndFieldPresence() {
        assertEquals("yMdhams", DateIntervalPatternCore.normalizeHourMetacharacters("yMdjjms", ULocale.US))
        assertEquals("Hmm", DateIntervalPatternCore.normalizeHourMetacharacters("BBBBBHHmm", ULocale.FRANCE))
        assertEquals("'h a' K bbbbb", DateIntervalPatternCore.adjustFieldWidth("KKKKKbbbbb", "ah", "'h a' h a", 2, false))
        assertEquals("'a''a'!", DateIntervalPatternCore.findReplaceInPattern("'a''a'a", "a", "!"))
        assertTrue(DateIntervalPatternCore.fieldExistsInSkeleton(Calendar.MONTH, "LLLLd"))
        assertTrue(DateIntervalPatternCore.fieldExistsInSkeleton(Calendar.YEAR, "rMd"))
        assertFalse(DateIntervalPatternCore.fieldExistsInSkeleton(Calendar.ERA, "rMd"))
    }
}
