package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUCache
import io.github.heapy.icu4k.impl.SimpleCache
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CalendarPatternDataCoreTest {
    private class Calendar(val type: String = "gregorian")
    private class Format(val pattern: String?,val override: String?,val locale: ULocale?) { var calendar: Calendar? = null }
    private class Owner: CalendarFormatCore.Owner<Calendar,Any,Format> {
        val trace = ArrayList<String>()
        val symbols = Any()
        var failure: String? = null
        var returnNull = false
        var configuration: CalendarFormatCore.FormatConfiguration<Calendar,Any>? = null
        private fun event(value: String) { trace.add(value); if (failure == value) error(value) }
        override fun getType(calendar: Calendar): String { event("type"); return calendar.type }
        override fun handleGetDateFormat(calendar: Calendar,pattern: String?,override: String?,locale: ULocale?): Format? {
            event("handle"); return if (returnNull) null else Format(pattern,override,locale)
        }
        override fun setCalendar(format: Format,calendar: Calendar) { event("setCalendar"); format.calendar = calendar }
        override fun createDateFormatSymbols(calendar: Calendar?,locale: ULocale?): Any { event("symbols"); return symbols }
        override fun createSimpleDateFormat(configuration: CalendarFormatCore.FormatConfiguration<Calendar,Any>): Format? {
            event("factory"); this.configuration = configuration
            return if (returnNull) null else Format(configuration.getPatternString(),configuration.getOverrideString(),configuration.getLocale())
        }
    }
    private class Cache: ICUCache<String,CalendarPatternDataCore.PatternData> {
        val values = HashMap<String,CalendarPatternDataCore.PatternData>()
        val trace = ArrayList<String>()
        override fun clear() { values.clear(); trace.add("clear") }
        override fun get(key: Any?): CalendarPatternDataCore.PatternData? { trace.add("get:$key"); return values[key] }
        override fun put(key: String,value: CalendarPatternDataCore.PatternData) { trace.add("put:$key"); values[key] = value }
    }
    private fun noGenerator(): (ULocale)->CalendarPatternDataCore.Generator = { error("unexpected generator: $it") }
    @Test fun standaloneIcu4jResourceAndAtTimeSnapshots() {
        // Recorded independently from ICU4J 78.3 with FULL date/FULL time.
        val cases = listOf(
            listOf("root","gregorian","y MMMM d, EEEE HH:mm:ss zzzz",null,"{1} {0}","{1} {0}"),
            listOf("en_US","gregorian","EEEE, MMMM d, y 'at' h:mm:ss\u202fa zzzz",null,"{1}, {0}","{1} 'at' {0}"),
            listOf("fr_FR","hebrew","EEEE d MMMM y G 'à' HH:mm:ss zzzz",null,"{1} {0}","{1} 'à' {0}"),
            listOf("he_IL","hebrew","EEEE, d בMMMM y בשעה H:mm:ss zzzz","E=hebr;,=hebr; =hebr;d=hebr; =hebr;ב=hebr;M=hebr; =hebr;y=hebr","{1}, {0}","{1} בשעה {0}"),
            listOf("ar_EG","gregorian","EEEE، d MMMM y في h:mm:ss a zzzz",null,"{1}، {0}","{1} في {0}"),
            listOf("fa_IR","hebrew","EEEE d MMMM y G ساعت H:mm:ss (zzzz)",null,"{1}، ساعت {0}","{1} ساعت {0}"),
        )
        for (row in cases) {
            val owner = Owner(); val patterns = CalendarPatternDataCore(noGenerator(),SimpleCache())
            val core = CalendarFormatCore(owner,patterns); val calendar = Calendar(row[1]!!); val locale = ULocale(row[0]!!)
            val format = core.getDateTimeFormat(calendar,locale,0,0)
            assertEquals(row[2],format.pattern); assertEquals(row[3],format.override); assertSame(locale,format.locale)
            assertSame(calendar,format.calendar); assertEquals(listOf("type","handle","setCalendar"),owner.trace)
            assertEquals(row[4],core.getDateTimePattern(calendar,locale,0)); assertEquals(row[5],core.getDateAtTimePattern(calendar,locale,0))
        }
    }
    @Test fun cacheKeysIgnoreOrdinaryKeywordsAndBypassHourCycleKeywords() {
        val cache = Cache(); val calls = ArrayList<String>()
        val core = CalendarPatternDataCore({ locale ->
            calls.add("factory:$locale")
            object: CalendarPatternDataCore.Generator { override fun getBestPattern(skeleton: String): String { calls.add(skeleton); return "generated:$skeleton" } }
        },cache)
        val first = core.make("gregorian",ULocale.US)
        assertSame(first,core.make("gregorian",ULocale("en_US@numbers=arab")))
        assertEquals(listOf("get:en_US+gregorian","put:en_US+gregorian","get:en_US+gregorian"),cache.trace)
        assertTrue(calls.isEmpty())
        for (id in listOf("en_US@hours=h23","en_US@rg=gbzzzz")) {
            cache.trace.clear(); calls.clear()
            val generated = core.make("gregorian",ULocale(id))
            assertEquals(listOf("generated:jmmsszzzz","generated:jmmssz","generated:jmmss","generated:jmm"),generated.patterns!!.take(4))
            assertTrue(generated.overrides!!.take(4).all { it == null })
            assertEquals(listOf("factory:$id","jmmsszzzz","jmmssz","jmmss","jmm"),calls)
            core.make("gregorian",ULocale(id)); assertEquals(10,calls.size); assertTrue(cache.trace.isEmpty())
        }
        calls.clear(); core.getPatternData(ULocale("en_ZZ"),"gregorian"); assertEquals(5,calls.size)
        val unknown = core.getPatternData(ULocale.US,"not-a-calendar")
        assertEquals(first.patterns!!.toList(),unknown.patterns!!.toList())
    }
    @Test fun onlyMissingResourcesTriggerFinalDefaultsAndOtherFailuresRetry() {
        var calls = 0
        val locale = ULocale("en_US@hours=h23")
        val cache = Cache()
        val core = CalendarPatternDataCore({ calls++; throw MissingResourceException("missing","test","key") },cache)
        val data = core.make("gregorian",locale)
        assertEquals(13,data.patterns!!.size); assertNull(data.overrides); assertEquals("HH:mm:ss z",data.pattern(0)); assertEquals("{1} {0}",data.getDateAtTimePattern(0))
        core.make("gregorian",locale); assertEquals(2,calls); assertTrue(cache.trace.isEmpty())
        assertFailsWith<MissingResourceException> { core.getPatternData(locale,"gregorian") }
        val broken = CalendarPatternDataCore({ calls++; error("broken generator") },cache)
        repeat(2) { assertFailsWith<IllegalStateException> { broken.make("gregorian",locale) } }
        assertEquals(5,calls)
        // Without hour keywords, the final fallback itself is cached by base name.
        val fallbackCache = Cache()
        val fallback = CalendarPatternDataCore({ throw MissingResourceException("missing","test","key") },fallbackCache)
        val first = fallback.make("gregorian",ULocale("en_ZZ"))
        assertSame(first,fallback.make("gregorian",ULocale("en_ZZ@numbers=arab")))
        assertEquals(listOf("get:en_ZZ+gregorian","put:en_ZZ+gregorian","get:en_ZZ+gregorian"),fallbackCache.trace)
    }
    @Test fun overrideExpansionPreservesSourceQuotesPunctuationAndSentinel() {
        assertEquals("y=arab;-=arab;M=arab;-=arab;d=arab",CalendarPatternDataCore.expandOverride("yyyy-MM-dd","arab"))
        assertEquals("H=latn;:=latn;m=latn; =latn; =latn;s=latn",CalendarPatternDataCore.expandOverride("HH:mm 'at' ss","latn"))
        assertEquals("a=hebr;a=hebr",CalendarPatternDataCore.expandOverride("a''a","hebr"))
        assertEquals("y=arab",CalendarPatternDataCore.expandOverride("y\uffffM","arab"))
        assertEquals("y=hebr",CalendarPatternDataCore.expandOverride(null,"y=hebr"))
        assertNull(CalendarPatternDataCore.mergeOverrideStrings(null,null,null,null))
        assertEquals("arab",CalendarPatternDataCore.mergeOverrideStrings(null,null,"arab","arab"))
        assertEquals("y=hebr;H=arab",CalendarPatternDataCore.mergeOverrideStrings("y","H","hebr","arab"))
    }
    @Test fun styleValidationFailureOrderAndFactoryCalendarAttachment() {
        val owner = Owner(); val cache = Cache()
        cache.values["en_US+gregorian"] = CalendarPatternDataCore.PatternData(Array(13) { if (it >= 8) "{1}/{0}" else "p$it" },arrayOfNulls(13),null)
        val core = CalendarFormatCore(owner,CalendarPatternDataCore(noGenerator(),cache))
        assertEquals("Illegal time style 4",assertFailsWith<IllegalArgumentException> { core.getDateTimeFormat(null,null,4,4) }.message)
        assertTrue(owner.trace.isEmpty())
        assertFailsWith<NullPointerException> { core.getDateTimeFormat(null,null,0,0) }; assertTrue(owner.trace.isEmpty())
        val calendar = Calendar()
        assertFailsWith<NullPointerException> { core.getDateTimeFormat(calendar,null,0,0) }; assertEquals(listOf("type"),owner.trace)
        owner.trace.clear()
        assertFailsWith<IllegalArgumentException> { core.getDateTimeFormat(calendar,ULocale.US,-1,-1) }; assertEquals(listOf("type"),owner.trace)
        owner.trace.clear(); val format = core.getDateTimeFormat(calendar,ULocale.US,2,3)
        assertEquals("p6/p3",format.pattern); assertSame(calendar,format.calendar)
        assertEquals(listOf("type","handle","setCalendar"),owner.trace)
        for (failure in listOf("handle","setCalendar")) {
            owner.trace.clear(); owner.failure = failure
            assertFailsWith<IllegalStateException> { core.getDateTimeFormat(calendar,ULocale.US,0,-1) }
            assertEquals(if (failure == "handle") listOf("type","handle") else listOf("type","handle","setCalendar"),owner.trace)
        }
        owner.failure = null; owner.trace.clear(); owner.returnNull = true
        assertFailsWith<NullPointerException> { core.getDateTimeFormat(calendar,ULocale.US,-1,0) }; assertEquals(listOf("type","handle"),owner.trace)
    }
    @Test fun defaultFormatConfigurationRetainsSymbolsCalendarAndLocaleAliases() {
        val owner = Owner(); val core = CalendarFormatCore(owner,CalendarPatternDataCore(noGenerator(),SimpleCache()))
        val calendar = Calendar(); val locale = ULocale.US
        val format = core.handleGetDateFormat(calendar,"y","hebr",locale)!!
        val configuration = owner.configuration!!
        assertEquals(listOf("symbols","factory"),owner.trace)
        assertSame(owner.symbols,configuration.getDateFormatSymbols()); assertSame(calendar,configuration.getCalendar()); assertSame(locale,configuration.getLocale())
        assertEquals("y",configuration.getPatternString()); assertEquals("hebr",configuration.getOverrideString()); assertNull(format.calendar)
        owner.trace.clear(); owner.failure = "symbols"
        assertFailsWith<IllegalStateException> { core.handleGetDateFormat(calendar,"y",null,locale) }; assertEquals(listOf("symbols"),owner.trace)
        owner.failure = null; owner.returnNull = true
        assertNull(core.handleGetDateFormat(null,null,null,null))
    }
    @Test fun patternArraysKeepAliasesAndBoundsOnAllPlatforms() {
        val patterns = Array<String?>(13) { "p$it" }; val atTime = arrayOf<String?>("a0",null,"a2","a3")
        val data = CalendarPatternDataCore.PatternData(patterns,null,atTime)
        assertEquals("p9",data.getDateTimePattern(0)); assertNull(data.getDateAtTimePattern(1))
        patterns[9] = "changed"; atTime[0] = "changed-at"
        assertEquals("changed",data.getDateTimePattern(0)); assertEquals("changed-at",data.getDateAtTimePattern(0))
        assertFailsWith<IndexOutOfBoundsException> { data.getDateAtTimePattern(-1) }
        assertFailsWith<IndexOutOfBoundsException> { data.pattern(13) }
        assertFailsWith<NullPointerException> { CalendarPatternDataCore.PatternData(null,null,null).getDateTimePattern(0) }
        assertEquals("p8",CalendarPatternDataCore.PatternData(patterns.copyOf(9),null,arrayOf("short")).getDateAtTimePattern(Int.MAX_VALUE))
    }
}
