@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.impl.ICUCache as SourceCache
import com.ibm.icu.text.DateTimePatternGenerator as Source
import com.ibm.icu.util.Calendar
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.ICUCache
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateTimePatternFactoryCoreOracleTest {
    private class A: SourceCache<String,Source> {
        val values=HashMap<String,Source>(); val trace=ArrayList<String>(); var failGet=false; var failPut=false; var intervene: (()->Unit)?=null
        override fun get(key: Any?): Source? { trace.add("get:$key"); if(failGet) error("get"); intervene?.let { intervene=null; it(); return null }; return values[key] }
        override fun put(key: String,value: Source) { trace.add("put:$key:${value.isFrozen}"); if(failPut) error("put"); values[key]=value }
        override fun clear() { values.clear() }
    }
    private class B: ICUCache<String,DateTimePatternGeneratorCore> {
        val values=HashMap<String,DateTimePatternGeneratorCore>(); val trace=ArrayList<String>(); var failGet=false; var failPut=false; var intervene: (()->Unit)?=null
        override fun get(key: Any?): DateTimePatternGeneratorCore? { trace.add("get:$key"); if(failGet) error("get"); intervene?.let { intervene=null; it(); return null }; return values[key] }
        override fun put(key: String,value: DateTimePatternGeneratorCore) { trace.add("put:$key:${value.isFrozen()}"); if(failPut) error("put"); values[key]=value }
        override fun clear() { values.clear() }
    }
    private fun factory(cache: B)=DateTimePatternFactoryCore(DateTimePatternInitializationCore(ICUResourceBundleReader.embeddedLoader),{ locale ->
        val loc=SourceLocale(locale.toString()); val calendar=Calendar.getInstance(loc)
        DateTimePatternInitializationCore.CalendarPatterns { Calendar.getDateAtTimePattern(calendar,loc,it) }
    },cache,{ ULocale(SourceLocale.getDefault(SourceLocale.Category.FORMAT).toString()) },{ DateTimePatternGeneratorCore(Source::class.java.desiredAssertionStatus()) })
    private fun isolated(block: (A,B,DateTimePatternFactoryCore)->Unit) {
        val field=Source::class.java.getDeclaredField("DTPNG_CACHE").apply { isAccessible=true }
        val old=field.get(null); val a=A(); val b=B()
        field.set(null,a)
        try { block(a,b,factory(b)) } finally { field.set(null,old) }
    }
    private fun state(a: Source,b: DateTimePatternGeneratorCore) {
        assertEquals(a.isFrozen,b.isFrozen()); assertEquals(a.decimal,b.getDecimal()); assertEquals(a.defaultHourFormatChar,b.getDefaultHourFormatChar())
        assertEquals(a.getSkeletons(LinkedHashMap()),b.getSkeletons(null))
        for(i in 0..3) assertEquals(a.getDateTimeFormat(i),b.getDateTimeFormat(i))
        for(i in 0..15) assertEquals(a.getAppendItemName(i),b.getAppendItemName(i))
        fun best(action: ()->String): Pair<String,String?> = try { "ok" to action() } catch(_: NullPointerException) { "NullPointerException" to null }
        for(request in listOf("yMd","jmm","Cmm","yMMMMdHms")) assertEquals(best { a.getBestPattern(request) },best { b.getBestPattern(request) })
    }
    @Test fun cacheIdentityThawAliasesAndUncachedFactoriesMatchSource()=isolated { a,b,factory ->
        for(id in listOf("en_US","fr_FR","fa_IR","ja_JP@calendar=japanese","en_US@hours=h23","en_US@numbers=arab")) {
            val sourceLocale=SourceLocale(id); val locale=ULocale(id)
            val first=Source.getFrozenInstance(sourceLocale); val target=factory.getFrozenInstance(locale); state(first,target)
            assertSame(first,Source.getFrozenInstance(sourceLocale)); assertSame(target,factory.getFrozenInstance(locale))
            val copy=Source.getInstance(sourceLocale); val targetCopy=factory.getInstance(locale); state(copy,targetCopy); state(first,target)
            assertFalse(first.isFrozen); assertFalse(target.isFrozen())
            copy.setAppendItemName(3,"shared"); targetCopy.setAppendItemName(3,"shared"); state(first,target)
            copy.decimal="copy"; targetCopy.setDecimal("copy"); state(first,target)
            first.decimal="cached"; target.setDecimal("cached"); state(Source.getInstance(sourceLocale),factory.getInstance(locale))
            state(Source.getInstanceNoStdPat(sourceLocale),factory.getInstanceNoStdPat(locale))
            assertEquals(a.trace,b.trace)
        }
        state(Source.getInstance(),factory.getInstance())
        val trace=a.trace.toList(); state(Source.getEmptyInstance(),factory.getEmptyInstance()); assertEquals(trace,a.trace); assertEquals(a.trace,b.trace)
        assertEquals(a.values.keys,b.values.keys)
    }
    @Test fun cacheReadAndPublicationFailuresRetryWithoutChangingPolicy()=isolated { a,b,factory ->
        a.failGet=true; b.failGet=true
        assertFailsWith<IllegalStateException> { Source.getFrozenInstance(SourceLocale.US) }; assertFailsWith<IllegalStateException> { factory.getFrozenInstance(ULocale.US) }
        a.failGet=false; b.failGet=false; a.failPut=true; b.failPut=true
        assertFailsWith<IllegalStateException> { Source.getFrozenInstance(SourceLocale.US) }; assertFailsWith<IllegalStateException> { factory.getFrozenInstance(ULocale.US) }
        assertTrue(a.values.isEmpty()); assertTrue(b.values.isEmpty()); assertEquals(a.trace,b.trace)
        a.failPut=false; b.failPut=false
        state(Source.getFrozenInstance(SourceLocale.US),factory.getFrozenInstance(ULocale.US)); assertEquals(a.trace,b.trace)
        assertFailsWith<NullPointerException> { Source.getFrozenInstance(null) }; assertFailsWith<NullPointerException> { factory.getFrozenInstance(null) }; assertEquals(a.trace,b.trace)
        repeat(2) {
            assertFailsWith<java.util.MissingResourceException> { Source.getFrozenInstance(SourceLocale("en_US@calendar=UNKNOWN")) }
            assertFailsWith<io.github.heapy.icu4k.util.MissingResourceException> { factory.getFrozenInstance(ULocale("en_US@calendar=UNKNOWN")) }
        }
        assertEquals(setOf("en_US"),a.values.keys); assertEquals(a.values.keys,b.values.keys); assertEquals(a.trace,b.trace)
    }
    @Test fun interveningPublicationIsOverwrittenAsInSource()=isolated { a,b,factory ->
        var nestedA: Source?=null; var nestedB: DateTimePatternGeneratorCore?=null
        a.intervene={ nestedA=Source.getFrozenInstance(SourceLocale.US) }; b.intervene={ nestedB=factory.getFrozenInstance(ULocale.US) }
        val outerA=Source.getFrozenInstance(SourceLocale.US); val outerB=factory.getFrozenInstance(ULocale.US)
        assertNotSame(nestedA,outerA); assertNotSame(nestedB,outerB)
        assertSame(outerA,a.values["en_US"]); assertSame(outerB,b.values["en_US"])
        state(outerA,outerB); state(nestedA!!,nestedB!!); assertEquals(a.trace,b.trace)
    }
}
