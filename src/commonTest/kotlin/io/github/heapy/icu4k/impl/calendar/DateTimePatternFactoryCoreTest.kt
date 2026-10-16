package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUCache
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateTimePatternFactoryCoreTest {
    private class Cache: ICUCache<String,DateTimePatternGeneratorCore> {
        val values=HashMap<String,DateTimePatternGeneratorCore>(); val trace=ArrayList<String>()
        var getAction: (()->Unit)?=null; var failPut=false
        override fun get(key: Any?): DateTimePatternGeneratorCore? { trace.add("get:$key"); getAction?.invoke(); return values[key] }
        override fun put(key: String,value: DateTimePatternGeneratorCore) { trace.add("put:$key:${value.isFrozen()}"); if(failPut) error("put"); values[key]=value }
        override fun clear() { values.clear() }
    }
    private fun factory(cache: Cache,created: MutableList<DateTimePatternGeneratorCore> = ArrayList(),calls: MutableList<String> = ArrayList()) = DateTimePatternFactoryCore(
        DateTimePatternInitializationCore(ICUResourceBundleReader.embeddedLoader),
        { locale -> calls.add("calendar:$locale"); DateTimePatternInitializationCore.CalendarPatterns { style -> calls.add("style:$style"); "{1} /$style/ {0}" } },
        cache,{ calls.add("default"); ULocale.US },{ DateTimePatternGeneratorCore().also { created.add(it) } },
    )
    @Test fun factoryCacheIdentityCloneAliasesAndUncachedModes() {
        val cache=Cache(); val created=ArrayList<DateTimePatternGeneratorCore>(); val calls=ArrayList<String>(); val factory=factory(cache,created,calls)
        val frozen=factory.getFrozenInstance(ULocale.US)
        assertTrue(frozen.isFrozen()); assertSame(frozen,factory.getFrozenInstance(ULocale.US))
        assertEquals(1,created.size); assertEquals(listOf("calendar:en_US","style:0","style:1","style:2","style:3"),calls)
        val copy=factory.getInstance(ULocale.US)
        assertNotSame(frozen,copy); assertFalse(copy.isFrozen()); assertFalse(frozen.isFrozen()) // ICU4J 007
        copy.setAppendItemName(3,"shared"); assertEquals("shared",frozen.getAppendItemName(3))
        copy.setDecimal("copy"); assertEquals(".",frozen.getDecimal())
        frozen.setDecimal("cached mutation"); assertEquals("cached mutation",factory.getInstance(ULocale.US).getDecimal())
        assertSame(frozen,factory.getFrozenInstance(ULocale.US)); assertFalse(frozen.isFrozen())
        calls.clear(); factory.getInstance(); assertEquals(listOf("default"),calls)
        val trace=cache.trace.toList()
        val first=factory.getInstanceNoStdPat(ULocale.US); val second=factory.getInstanceNoStdPat(ULocale.US)
        assertNotSame(first,second); assertFalse(first.isFrozen()); assertEquals("{1} {0}",first.getDateTimeFormat())
        val empty=factory.getEmptyInstance()
        assertNotSame(empty,factory.getEmptyInstance()); assertEquals(trace,cache.trace)
        assertFailsWith<NullPointerException> { empty.getBestPattern("Cmm") }
        assertEquals(5,created.size)
    }
    @Test fun publicationFailuresRetryAndKeysIncludeAllLocaleKeywords() {
        val cache=Cache(); val created=ArrayList<DateTimePatternGeneratorCore>(); val factory=factory(cache,created)
        cache.failPut=true
        assertFailsWith<IllegalStateException> { factory.getFrozenInstance(ULocale.US) }
        assertTrue(created.single().isFrozen()); assertTrue(cache.values.isEmpty())
        cache.failPut=false
        val result=factory.getFrozenInstance(ULocale.US)
        assertNotSame(created.first(),result); assertEquals(2,created.size)
        assertNotSame(result,factory.getFrozenInstance(ULocale("en_US@numbers=latn")))
        assertEquals(setOf("en_US","en_US@numbers=latn"),cache.values.keys)
        val size=cache.trace.size
        assertFailsWith<NullPointerException> { factory.getFrozenInstance(null) }; assertEquals(size,cache.trace.size)
        cache.getAction={ error("get") }
        assertFailsWith<IllegalStateException> { factory.getFrozenInstance(ULocale.US) }; assertEquals(3,created.size)
    }
    @Test fun sourceReadThenPublishCanOverwriteAnInterveningPublication() {
        val cache=Cache(); val created=ArrayList<DateTimePatternGeneratorCore>(); val factory=factory(cache,created)
        var nested: DateTimePatternGeneratorCore?=null
        // Return a miss even after reentrant publication, modeling two concurrent misses.
        val racing=object: ICUCache<String,DateTimePatternGeneratorCore> {
            var first=true
            override fun get(key: Any?): DateTimePatternGeneratorCore? {
                if(first) { first=false; nested=factory.getFrozenInstance(ULocale.US); return null }
                return cache.get(key)
            }
            override fun put(key: String,value: DateTimePatternGeneratorCore) = cache.put(key,value)
            override fun clear() = cache.clear()
        }
        val outer=DateTimePatternFactoryCore(DateTimePatternInitializationCore(ICUResourceBundleReader.embeddedLoader),{ DateTimePatternInitializationCore.CalendarPatterns { "{1} {0}" } },racing).getFrozenInstance(ULocale.US)
        assertNotSame(nested,outer); assertSame(outer,cache.values["en_US"]); assertTrue(outer.isFrozen()); assertTrue(nested!!.isFrozen())
    }
}
