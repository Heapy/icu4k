package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatDefaultPatternCoreTest {
    @Test fun reentrantConstructionUsesUpdatedCachedLocale() {
        val french = ULocale("fr")
        val expected = SimpleDateFormatDefaultPatternCore().getPattern(french)
        lateinit var core: SimpleDateFormatDefaultPatternCore
        var enter = true
        core = SimpleDateFormatDefaultPatternCore(createCalendar = { locale ->
            if (enter) {
                enter = false
                assertEquals(expected, core.getPattern(french))
            }
            Calendar.getInstance(locale)
        })
        assertEquals(expected, core.getPattern(ULocale.US))
        assertEquals(expected, core.getPattern(french))
    }
    @Test fun shortPatternUsesRawResourcesAndOneEntryCache() {
        var calls=0
        val core=SimpleDateFormatDefaultPatternCore(createCalendar={ calls++; Calendar.getInstance(it) })
        assertEquals("M/d/yy, h:mm\u202fa",core.getPattern(ULocale.US))
        assertEquals("M/d/yy, h:mm\u202fa",core.getPattern(ULocale.US))
        assertEquals(1,calls)
        assertNotNull(core.getPattern(ULocale("fr")))
        core.getPattern(ULocale.US)
        assertEquals(3,calls)
    }
    @Test fun nonMissingFailureLeavesOldPatternCachedForNewLocale() {
        var fail=false
        var calls=0
        val core=SimpleDateFormatDefaultPatternCore(createCalendar={ calls++; if(fail) throw IllegalStateException("failure"); Calendar.getInstance(it) })
        val first=core.getPattern(ULocale.US)
        fail=true
        assertFailsWith<IllegalStateException> { core.getPattern(ULocale("fr")) }
        assertEquals(first,core.getPattern(ULocale("fr")))
        assertEquals(2,calls)
        assertFailsWith<IllegalStateException> { core.getPattern(ULocale("ru")) }
        assertEquals(first,core.getPattern(ULocale("ru")))
    }
    @Test fun onlyMissingResourcesInsideTryChooseFallback() {
        val missing=SimpleDateFormatDefaultPatternCore(atTimePattern={ _,_,_->throw MissingResourceException("missing","test","key") })
        assertEquals("yy/MM/dd HH:mm",missing.getPattern(ULocale.US))
        val outside=SimpleDateFormatDefaultPatternCore(createCalendar={ throw MissingResourceException("missing","test","key") })
        assertFailsWith<MissingResourceException> { outside.getPattern(ULocale.US) }
        assertNull(outside.getPattern(ULocale.US))
    }
    @Test fun typeMismatchAndHourKeywordsKeepSourceRawPatternSelection() {
        val core=SimpleDateFormatDefaultPatternCore()
        val ordinary=core.getPattern(ULocale.US)
        assertEquals(ordinary,core.getPattern(ULocale("en_US@hours=h23")))
        assertEquals(ordinary,core.getPattern(ULocale("en_US@rg=gbzzzz")))
        val hebrew=ULocale("he_IL@calendar=hebrew")
        assertFailsWith<UResourceTypeMismatchException> { core.getPattern(hebrew) }
        assertEquals(ordinary,core.getPattern(hebrew))
    }
}
