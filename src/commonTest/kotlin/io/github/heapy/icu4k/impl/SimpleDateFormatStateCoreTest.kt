@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatStateCoreTest {
    @Test fun initializationAndLazyCenturyLeaveCalendarAlone() {
        val f = SimpleDateFormatStateFixture("en_US", "yyyy-MM-dd")
        assertTrue(f.getNumberFormat() is DateNumberFormat)
        assertNull(f.state.defaultCenturyStart)
        f.getCalendar()!!.setTimeZone(TimeZone.getTimeZone("Etc/UTC")); f.getCalendar()!!.setTimeInMillis(123456789L)
        val before = f.getCalendar()!!.getTimeInMillis()
        assertEquals(1890, f.state.centuryStartYear())
        assertEquals(before, f.getCalendar()!!.getTimeInMillis())
        val start = Date(0)
        f.state.setCenturyStart(start)
        assertSame(start, f.state.centuryStart())
        assertEquals(0L, f.getCalendar()!!.getTimeInMillis())
        assertEquals(1970,f.state.centuryStartYear())
    }
    @Test fun fullAndPartialOverridesPreserveMutationAndFailureOrder() {
        val f = SimpleDateFormatStateFixture("en_US", "y-M-d")
        val nf = NumberFormat.getInstance(ULocale("ar"))
        assertFailsWith<IllegalArgumentException> { f.setNumberFormat("y!",nf) }
        assertFalse(nf.isGroupingUsed())
        assertSame(nf,f.getNumberFormat('y'))
        assertTrue(f.state.numbers.useLocalZeroPaddingNumberFormat)
        f.setNumberFormat("Md",nf)
        assertFalse(f.state.numbers.useLocalZeroPaddingNumberFormat)
        assertSame(f.getNumberFormat('M'),f.getNumberFormat('d'))
        f.setNumberFormat(NumberFormat.getInstance(ULocale.US))
        assertNull(f.state.overrideMap); assertNull(f.state.numberFormatters)
        assertNotSame(nf,f.getNumberFormat('M'))
        assertFailsWith<NullPointerException> { SimpleDateFormatStateFixture("en_US","y", "latn;y=thai",false) }
    }
    @Test fun localizedPatternRetainsSharedCompiledItemsAndFlags() {
        val f = SimpleDateFormatStateFixture("en_US", "yyyy-MM-dd")
        f.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        assertEquals("1970-01-01",f.format(Date(0)))
        val old = f.state.compiledItems()
        f.state.applyLocalizedPattern("HH:mm")
        assertSame(old,f.state.compiledItems())
        assertFalse(f.state.flags.hasMinute)
        assertEquals("1970-01-01",f.format(Date(0)))
        val pos = ParsePosition(0)
        assertNotNull(f.parse("2024-02-03",pos))
        assertEquals(10,pos.index)
        assertEquals(1,f.getCalendar()!!.get(Calendar.MONTH))
        f.state.applyPattern("HH:mm")
        assertTrue(f.state.flags.hasMinute)
        assertEquals("00:00",f.format(Date(0)))
    }
    @Test fun japaneseGannenAndCloneSharing() {
        val f = SimpleDateFormatStateFixture("ja@calendar=japanese", "y年M月d日")
        assertEquals("y=jpanyear",f.state.numberingOverride)
        f.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        assertTrue(f.format(Date(1556668800000L)).startsWith("元年"))
        val custom = NumberFormat.getInstance(ULocale("ar"))
        f.setNumberFormat("M",custom)
        val clone = f.clone()
        assertSame(f.state.overrideMap,clone.state.overrideMap)
        assertSame(f.getNumberFormat('M'),clone.getNumberFormat('M'))
        assertNotSame(f.state.symbols,clone.state.symbols)
        f.state.applyPattern("y-M-d")
        assertNull(f.state.overrideMap)
        assertNotNull(clone.state.overrideMap)
    }
    @Test fun nullPatternResetsFlagsAndNullableTranslationsRemainLazy() {
        val f = SimpleDateFormatStateFixture("en_US", "mm:ss")
        assertFailsWith<NullPointerException> { f.state.applyPattern(null) }
        assertFalse(f.state.flags.hasMinute); assertFalse(f.state.flags.hasSecond)
        assertNull(f.state.pattern)
        val g = SimpleDateFormatStateFixture("en_US", "'literal'")
        g.state.symbols!!.setLocalPatternChars(null)
        assertEquals("'literal'",g.state.toLocalizedPattern())
        g.state.applyLocalizedPattern("'other'")
        assertEquals("'other'",g.state.pattern)
    }
    @Test fun timeZoneDigitSynchronizationAndFrozenOwnership() {
        val f = SimpleDateFormatStateFixture("en_US", "O")
        val zone = f.state.getFrozenTimeZoneFormat()
        assertTrue(zone.isFrozen())
        val custom = zone.cloneAsThawed().setGMTZeroFormat("ZERO")
        f.state.setTimeZoneFormat(custom)
        assertNotSame(custom,f.state.zoneFormat)
        assertTrue(f.state.zoneFormat!!.isFrozen())
        f.setNumberFormat(NumberFormat.getInstance(ULocale("ar_EG")))
        assertNotEquals("ZERO",f.state.zoneFormat!!.getGMTZeroFormat())
        assertEquals("٠١٢٣٤٥٦٧٨٩",f.state.zoneFormat!!.getGMTOffsetDigits())
    }
}
