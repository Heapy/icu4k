@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.impl

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.text.NumberFormat as SourceNumber
import com.ibm.icu.text.DecimalFormat as SourceDecimal
import com.ibm.icu.text.TimeZoneFormat as SourceZoneFormat
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone as SourceZone
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatStateCoreOracleTest {
    private fun field(a: Source, name: String): Any? = Source::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(a)
    private fun put(a: Source, name: String, value: Any?) { Source::class.java.getDeclaredField(name).also { it.isAccessible = true }.set(a,value) }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val t = if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        if(t is VirtualMachineError) throw t
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName + ":" + t.message }
    }
    private fun pair(id: String, pattern: String, override: String? = null): Pair<Source,SimpleDateFormatStateFixture> {
        val a = if(override == null) Source(pattern,SourceLocale(id)) else Source(pattern,override,SourceLocale(id))
        put(a,"defaultCenturyBase",0L)
        val b = SimpleDateFormatStateFixture(id,pattern,override,override == null)
        b.assertions = Source::class.java.desiredAssertionStatus()
        a.timeZone = SourceZone.getTimeZone("Etc/UTC"); b.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        return a to b
    }
    private fun number(a: SourceNumber?): Any? = a?.let { listOf(it.javaClass.simpleName,it.isGroupingUsed,it.isParseIntegerOnly,it.minimumIntegerDigits,it.maximumIntegerDigits,outcome { it.format(-1234L) },if(it is SourceDecimal) it.toPattern() else null) }
    private fun number(a: NumberFormat?): Any? = a?.let { listOf(it::class.simpleName,it.isGroupingUsed(),it.isParseIntegerOnly(),it.getMinimumIntegerDigits(),it.getMaximumIntegerDigits(),outcome { it.format(-1234L) },if(it is DecimalFormat) it.toPattern() else null) }
    private fun compareState(a: Source, b: SimpleDateFormatStateFixture) {
        val s = b.state
        assertEquals(a.toPattern(),s.pattern)
        assertEquals(field(a,"override"),s.numberingOverride)
        assertEquals(field(a,"useFastFormat"),s.useFastFormat)
        assertEquals(field(a,"hasMinute"),s.flags.hasMinute); assertEquals(field(a,"hasSecond"),s.flags.hasSecond); assertEquals(field(a,"hasHanYearChar"),s.flags.hasHanYearChar)
        assertEquals(field(a,"useLocalZeroPaddingNumberFormat"),s.numbers.useLocalZeroPaddingNumberFormat)
        assertEquals(field(a,"defaultCenturyStart"),s.defaultCenturyStart); assertEquals(field(a,"defaultCenturyStartYear"),s.defaultCenturyStartYear)
        assertEquals(a.calendar.type,b.getCalendar()!!.getType())
        assertEquals(a.getLocale(SourceLocale.VALID_LOCALE)?.toString(),b.getLocale(ULocale.VALID_LOCALE)?.toString())
        assertEquals(a.getLocale(SourceLocale.ACTUAL_LOCALE)?.toString(),b.getLocale(ULocale.ACTUAL_LOCALE)?.toString())
        assertEquals(number(a.numberFormat),number(b.getNumberFormat()))
        val am = field(a,"overrideMap") as Map<Char,String?>?
        assertEquals(am?.keys,s.overrideMap?.keys)
        assertEquals((field(a,"numberFormatters") as Map<*,*>?)?.size,s.numberFormatters?.size)
        for(ch in SimpleDateFormatStateCore.PATTERN_CHARS) assertEquals(number(a.getNumberFormat(ch)),number(b.getNumberFormat(ch)),"${s.locale}/${s.pattern}/$ch")
        if(am != null) for(x in am.keys) for(y in am.keys) assertEquals(am[x] == am[y],s.overrideMap!![x] == s.overrideMap!![y])
    }
    private fun render(a: Source,b: SimpleDateFormatStateFixture,millis: Long) {
        assertEquals(outcome { a.format(java.util.Date(millis)) },outcome { b.format(Date(millis)) },"${b.state.locale}/${b.state.pattern}/$millis")
    }
    @Test fun numberingOverridesBypassRegisteredNumberFactories() {
        var acount=0; var bcount=0
        val ak=SourceNumber.registerFactory(object : SourceNumber.SimpleNumberFormatFactory(SourceLocale.US,false) {
            override fun createFormat(loc: SourceLocale?, formatType: Int): SourceNumber? { acount++; return SourceDecimal("'FACTORY'0") }
        })
        val bk=NumberFormat.registerFactory(object : SimpleNumberFormatFactory(ULocale.US,false) {
            override fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? { bcount++; return DecimalFormat("'FACTORY'0") }
        })
        try {
            assertEquals("FACTORY1",SourceNumber.getInstance(SourceLocale.US).format(1L))
            assertEquals("FACTORY1",NumberFormat.getInstance(ULocale.US).format(1L))
            val beforeA=acount; val beforeB=bcount
            val (a,b)=pair("en_US","y-M-d","y=latn")
            assertEquals(beforeA,acount); assertEquals(beforeB,bcount)
            assertEquals("1",a.getNumberFormat('y').format(1L)); assertEquals("1",b.getNumberFormat('y')!!.format(1L))
            compareState(a,b)
        } finally { SourceNumber.unregister(ak); NumberFormat.unregister(bk) }
    }
    @Test fun initializedStockFormatsOverridesAndJapaneseTransitions() {
        for(id in listOf("en_US","ru","ar_EG","fa","en@numbers=mathbold","en@numbers=hanidec","en@numbers=roman","he@calendar=hebrew","ja@calendar=japanese","zh@calendar=chinese")) {
            for(override in listOf(null,"","latn","y=hebr","y=thai;M=thai;d=deva","y=latn;M=latn","y=unknown")) {
                val (a,b) = pair(id,"G y年 MMMM d HH:mm:ss.SSS OOOO",override)
                compareState(a,b)
                for(millis in listOf(0L,1556668800123L,1710054000000L)) render(a,b,millis)
                for(pattern in listOf("y-M-d","y年M月d日","HH:mm","'年'mm")) {
                    assertEquals(outcome { a.applyPattern(pattern) },outcome { b.state.applyPattern(pattern) })
                    compareState(a,b); render(a,b,1556668800000L)
                }
            }
        }
    }
    @Test fun sharedLocalizedCacheAcrossBothEnginesAndNullFlags() {
        for(prewarm in listOf("format","parse","none")) {
            val (a,b) = pair("en_US","yyyy-MM-dd")
            if(prewarm == "format") render(a,b,0)
            if(prewarm == "parse") {
                a.parse("2024-02-03",java.text.ParsePosition(0)); b.parse("2024-02-03",ParsePosition(0))
            }
            for(pattern in listOf("HH:mm","mm:ss","'literal'","yyyy")) {
                assertEquals(outcome { a.applyLocalizedPattern(pattern) },outcome { b.state.applyLocalizedPattern(pattern) })
                compareState(a,b); render(a,b,123456L)
                for(text in listOf("2024-02-03","12:34","literal","")) {
                    val ap=java.text.ParsePosition(0); val bp=ParsePosition(0)
                    assertEquals(outcome { a.parse(text,ap)?.time },outcome { b.parse(text,bp)?.getTime() })
                    assertEquals(listOf(ap.index,ap.errorIndex),listOf(bp.index,bp.errorIndex))
                }
            }
            assertEquals(outcome { a.applyPattern(null) },outcome { b.state.applyPattern(null) })
            compareState(a,b); render(a,b,0)
        }
    }
    @Test fun partialCustomFormatsFailuresAndCloneOwnership() {
        val (a,b)=pair("en_US","y-MM-dd")
        val an=SourceDecimal("0000"); val bn=DecimalFormat("0000")
        for(fields in listOf("y!","Md","","LrB","x?")) {
            assertEquals(outcome { a.setNumberFormat(fields,an) },outcome { b.setNumberFormat(fields,bn) })
            compareState(a,b); render(a,b,0)
            assertEquals(an.isGroupingUsed,bn.isGroupingUsed())
        }
        val ac=a.clone() as Source; val bc=b.clone()
        assertSame(a.getNumberFormat('y'),ac.getNumberFormat('y')); assertSame(b.getNumberFormat('y'),bc.getNumberFormat('y'))
        assertSame(field(a,"overrideMap"),field(ac,"overrideMap")); assertSame(b.state.overrideMap,bc.state.overrideMap)
        a.setNumberFormat("M",SourceDecimal("00")); b.setNumberFormat("M",DecimalFormat("00"))
        compareState(ac,bc); render(ac,bc,0)
        a.numberFormat=SourceNumber.getInstance(SourceLocale("ar")); b.setNumberFormat(NumberFormat.getInstance(ULocale("ar")))
        compareState(a,b); compareState(ac,bc)
    }
    @Test fun centuryIdentityCloneAndLocaleIndependentMutations() {
        for(id in listOf("en_US","en@calendar=hebrew","ja@calendar=japanese")) {
            val (a,b)=pair(id,"yy-MM-dd")
            a.calendar.timeInMillis=123456L; b.getCalendar()!!.setTimeInMillis(123456L)
            assertEquals(a.get2DigitYearStart().time,b.state.centuryStart()!!.getTime())
            assertEquals(a.calendar.timeInMillis,b.getCalendar()!!.getTimeInMillis())
            compareState(a,b)
            val shared=java.util.Date(12_960_000_000L)
            a.set2DigitYearStart(shared); b.state.setCenturyStart(shared)
            assertSame(shared,a.get2DigitYearStart()); assertSame(shared,b.state.centuryStart())
            val ac=a.clone() as Source; val bc=b.clone()
            shared.time=0
            assertSame(shared,ac.get2DigitYearStart()); assertSame(shared,bc.state.centuryStart())
            compareState(ac,bc)
        }
    }
    @Test fun invalidOverridesRetainSourceConstructionFailures() {
        for(value in listOf("latn;y=thai","y=thai;latn;M=deva","latn;",";y=thai","=thai","yy=thai","y=thai;y=deva","y=thai;;M=latn")) {
            assertEquals(outcome { Source("y-M-d",value,SourceLocale.US); "ok" },outcome { SimpleDateFormatStateFixture("en_US","y-M-d",value,false); "ok" },value)
        }
    }
    @Test fun nullableLocalizedCharactersAndTimeZoneOwnership() {
        val (a,b)=pair("en_US","'literal'")
        a.dateFormatSymbols=a.dateFormatSymbols.also { it.localPatternChars=null }
        b.state.symbols!!.setLocalPatternChars(null)
        assertEquals(a.toLocalizedPattern(),b.state.toLocalizedPattern())
        assertEquals(outcome { a.applyLocalizedPattern("yyyy") },outcome { b.state.applyLocalizedPattern("yyyy") })
        val az=a.timeZoneFormat; val bz=b.state.getFrozenTimeZoneFormat()
        assertTrue(az.isFrozen); assertTrue(bz.isFrozen())
        val ax=az.cloneAsThawed().setGMTZeroFormat("ZERO"); val bx=bz.cloneAsThawed().setGMTZeroFormat("ZERO")
        a.setTimeZoneFormat(ax); b.state.setTimeZoneFormat(bx)
        assertNotSame(ax,a.timeZoneFormat); assertNotSame(bx,b.state.getFrozenTimeZoneFormat())
        for(id in listOf("ar_EG","en@numbers=mathbold","fa","en_US")) {
            a.numberFormat=SourceNumber.getInstance(SourceLocale(id)); b.setNumberFormat(NumberFormat.getInstance(ULocale(id)))
            assertEquals(a.timeZoneFormat.gmtOffsetDigits,b.state.getFrozenTimeZoneFormat().getGMTOffsetDigits())
            assertEquals(a.timeZoneFormat.gmtZeroFormat,b.state.getFrozenTimeZoneFormat().getGMTZeroFormat())
        }
    }
}
