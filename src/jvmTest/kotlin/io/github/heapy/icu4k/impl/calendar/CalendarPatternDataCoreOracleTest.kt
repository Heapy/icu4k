@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateFormat
import com.ibm.icu.text.SimpleDateFormat
import com.ibm.icu.util.Calendar
import com.ibm.icu.util.GregorianCalendar
import com.ibm.icu.util.TimeZone
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.SimpleCache
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class CalendarPatternDataCoreOracleTest {
    private fun core() = CalendarPatternDataCore.withCommonGenerator(ICUResourceBundleReader.embeddedLoader,SimpleCache())
    private class Probe(private val requestedType: String?): GregorianCalendar(TimeZone.getTimeZone("Etc/GMT"),SourceLocale.US) {
        private var ready = false
        var record: List<String?>? = null
        private val format = SimpleDateFormat("y",SourceLocale.ROOT)
        init { ready = true }
        override fun getType(): String? = if (ready) requestedType else super.getType()
        override fun handleGetDateFormat(pattern: String?, override: String?, locale: SourceLocale?): DateFormat {
            record = listOf(pattern,override,locale?.toString())
            return format
        }
    }
    private class Format(val record: List<String?>) { var calendar: Probe? = null }
    private fun formatter(patterns: CalendarPatternDataCore) = CalendarFormatCore(object: CalendarFormatCore.Owner<Probe,String,Format> {
        override fun getType(calendar: Probe) = calendar.type
        override fun handleGetDateFormat(calendar: Probe,pattern: String?,override: String?,locale: ULocale?) = Format(listOf(pattern,override,locale?.toString()))
        override fun setCalendar(format: Format,calendar: Probe) { format.calendar = calendar }
        override fun createDateFormatSymbols(calendar: Probe?,locale: ULocale?) = "symbols"
        override fun createSimpleDateFormat(configuration: CalendarFormatCore.FormatConfiguration<Probe,String>) = error("not used by overridden handler")
    },patterns)
    private fun outcome(action: ()->Any?): Any? = try { action() } catch (e: Exception) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        if (cause is VirtualMachineError) throw cause
        if (cause !is Exception) throw cause
        (if (cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.name) to if (cause is NullPointerException || cause is IndexOutOfBoundsException) null else cause.message
    }
    private fun arrays(data: Any): List<List<String?>?> = listOf("patterns","overrides","atTimePatterns").map { name ->
        @Suppress("UNCHECKED_CAST")
        (patternClass.getDeclaredField(name).apply { isAccessible = true }.get(data) as Array<String?>?)?.toList()
    }
    @Test fun resourcesFallbacksOverridesAndGeneratedHourCycles() {
        val target = core()
        for (id in LOCALES) for (type in TYPES) {
            val expected = load.invoke(null,SourceLocale(id),type)
            val actual = target.getPatternData(ULocale(id),type)
            assertEquals(arrays(expected),listOf(actual.patterns?.toList(),actual.overrides?.toList(),actual.atTimePatterns?.toList()),"$id/$type")
        }
    }
    @Test fun styleResolutionDispatchAndCalendarAliasing() {
        val target = formatter(core())
        for (id in LOCALES) for (type in TYPES) {
            val calendar = Probe(type)
            for (date in -1..3) for (time in -1..3) {
                val expected = outcome {
                    val format = calendar.getDateTimeFormat(date,time,SourceLocale(id))
                    assertSame(calendar,format.calendar)
                    calendar.record
                }
                val actual = outcome {
                    val format = target.getDateTimeFormat(calendar,ULocale(id),date,time)
                    assertSame(calendar,format.calendar)
                    format.record
                }
                assertEquals(expected,actual,"$id/$type/$date/$time")
            }
            for (date in -2..5) {
                assertEquals(outcome { Calendar.getDateTimePattern(calendar,SourceLocale(id),date) },outcome { target.getDateTimePattern(calendar,ULocale(id),date) },"glue/$id/$type/$date")
                assertEquals(outcome { Calendar.getDateAtTimePattern(calendar,SourceLocale(id),date) },outcome { target.getDateAtTimePattern(calendar,ULocale(id),date) },"atTime/$id/$type/$date")
            }
        }
        for (date in listOf(Int.MIN_VALUE,-2,-1,0,4,Int.MAX_VALUE)) for (time in listOf(Int.MIN_VALUE,-2,-1,0,4,Int.MAX_VALUE)) {
            assertEquals(outcome { formatHelper.invoke(null,null,null,date,time) },outcome { target.getDateTimeFormat(null,null,date,time) },"validation/$date/$time")
        }
    }
    @Test fun sourceOverrideGrammarIncludingQuotedTextAndIteratorSentinel() {
        val random = Random(783)
        val alphabet = "yMdHmsz /:='\uffff\u0000😀"
        val patterns = listOf<String?>(null,"","yyyy-MM-dd","HH:mm 'at' ss","'quoted' yyyy","a''a"," y y ","y\uffffM") + List(2000) { buildString { repeat(random.nextInt(40)) { append(alphabet[random.nextInt(alphabet.length)]) } } }
        for (pattern in patterns) for (override in listOf(null,"","arab","hebr","y=hebr","m=thai;y=deva")) {
            assertEquals(outcome { expand.invoke(null,pattern,override) },outcome { CalendarPatternDataCore.expandOverride(pattern,override) },"expand/$pattern/$override")
        }
        repeat(10000) {
            val date = patterns[random.nextInt(patterns.size)]; val time = patterns[random.nextInt(patterns.size)]
            val dateOverride = listOf(null,"","arab","hebr","y=deva")[random.nextInt(5)]
            val timeOverride = listOf(null,"","arab","hebr","H=latn")[random.nextInt(5)]
            assertEquals(outcome { merge.invoke(null,date,time,dateOverride,timeOverride) },outcome { CalendarPatternDataCore.mergeOverrideStrings(date,time,dateOverride,timeOverride) })
        }
    }
    @Test fun malformedPatternDataRetainsArrayBoundsNullsAndAliasing() {
        for (size in listOf(-1,0,1,8,9,12,13,14)) for (atSize in listOf(-1,0,3,4,5)) {
            val values = if (size < 0) null else Array<String?>(size) { "pattern-$it" }
            val atTime = if (atSize < 0) null else Array<String?>(atSize) { if (it % 2 == 0) null else "at-$it" }
            val source = patternConstructor.newInstance(values,null,atTime)
            val target = CalendarPatternDataCore.PatternData(values,null,atTime)
            for (style in listOf(Int.MIN_VALUE,-10,-2,-1,0,1,2,3,4,10,Int.MAX_VALUE)) {
                assertEquals(outcome { dateTime.invoke(source,style) },outcome { target.getDateTimePattern(style) },"$size/$atSize/$style")
                assertEquals(outcome { at.invoke(source,style) },outcome { target.getDateAtTimePattern(style) },"at/$size/$atSize/$style")
            }
            values?.fill("mutated")
            assertEquals(outcome { dateTime.invoke(source,0) },outcome { target.getDateTimePattern(0) })
        }
    }
    @Test fun malformedCachedArraysMatchStyleFailureTiming() {
        val keyType = "icu4k-oracle-malformed"
        val sourceCacheField = Calendar::class.java.getDeclaredField("PATTERN_CACHE").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val sourceCache = sourceCacheField.get(null) as com.ibm.icu.impl.ICUCache<String,Any>
        val targetCache = SimpleCache<String,CalendarPatternDataCore.PatternData>()
        val target = formatter(CalendarPatternDataCore({ error("cached data must not generate patterns") },targetCache))
        val calendar = Probe(keyType)
        for (size in listOf(-1,0,1,4,8,9,13)) for (overridesSize in listOf(-1,0,4,13)) for (atTimeSize in listOf(-1,3,4)) {
            val values = if (size < 0) null else Array<String?>(size) { if (it >= 8) "{1} {0}" else "p$it" }
            val overrides = if (overridesSize < 0) null else Array<String?>(overridesSize) { if (it % 2 == 0) "arab" else null }
            val atTime = if (atTimeSize < 0) null else arrayOfNulls<String>(atTimeSize)
            sourceCache.put("en_US+"+keyType,patternConstructor.newInstance(values,overrides,atTime))
            targetCache.put("en_US+"+keyType,CalendarPatternDataCore.PatternData(values,overrides,atTime))
            for (date in -1..3) for (time in -1..3) {
                assertEquals(outcome { calendar.getDateTimeFormat(date,time,SourceLocale.US); calendar.record },outcome { target.getDateTimeFormat(calendar,ULocale.US,date,time).record },"cached/$size/$overridesSize/$atTimeSize/$date/$time")
            }
        }
    }
    companion object {
        private val LOCALES = listOf("root","und","en","en_US","en_GB","en_AU","fr_CA","ar_EG","fa_IR","he_IL","ja_JP","zh_Hant_TW","en_ZZ","und_GB","xx_YY","en@hours=h23","en@hours=h11","en@rg=gbzzzz","en@numbers=arab")
        private val TYPES = listOf("gregorian","hebrew","chinese","dangi","islamic-umalqura","unknown",null)
        private val intType = Int::class.javaPrimitiveType!!
        private val patternClass = Class.forName("com.ibm.icu.util.Calendar\$PatternData")
        private val patternConstructor = patternClass.getDeclaredConstructor(Array<String>::class.java,Array<String>::class.java,Array<String>::class.java).apply { isAccessible = true }
        private val dateTime = patternClass.getDeclaredMethod("getDateTimePattern",intType).apply { isAccessible = true }
        private val at = patternClass.getDeclaredMethod("getDateAtTimePattern",intType).apply { isAccessible = true }
        private val load = Calendar::class.java.getDeclaredMethod("getPatternData",SourceLocale::class.java,String::class.java).apply { isAccessible = true }
        private val formatHelper = Calendar::class.java.getDeclaredMethod("formatHelper",Calendar::class.java,SourceLocale::class.java,intType,intType).apply { isAccessible = true }
        private val expand = Calendar::class.java.getDeclaredMethod("expandOverride",String::class.java,String::class.java).apply { isAccessible = true }
        private val merge = Calendar::class.java.getDeclaredMethod("mergeOverrideStrings",String::class.java,String::class.java,String::class.java,String::class.java).apply { isAccessible = true }
    }
}
