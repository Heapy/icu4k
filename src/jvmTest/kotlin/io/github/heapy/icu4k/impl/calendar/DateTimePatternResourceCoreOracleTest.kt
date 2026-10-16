@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateTimePatternGenerator as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class DateTimePatternResourceCoreOracleTest {
    private fun outcome(action: ()->Unit): String=try { action(); "ok" } catch(error: Throwable) {
        val cause=if(error is InvocationTargetException) error.targetException else error
        if(cause !is Exception) throw cause
        cause.javaClass.simpleName
    }
    @Test fun standardAndCLDRLoadingMatchesSourceLocalesCalendarsAndRepeatedLoads() {
        val resources=DateTimePatternResourceCore(ICUResourceBundleReader.embeddedLoader)
        val ctor=Source::class.java.getDeclaredConstructor().apply { isAccessible=true }
        val canonical=Source::class.java.getDeclaredMethod("addCanonicalItems").apply { isAccessible=true }
        val standard=Source::class.java.getDeclaredMethod("addICUPatterns",Source.PatternInfo::class.java,SourceLocale::class.java).apply { isAccessible=true }
        val cldr=Source::class.java.getDeclaredMethod("addCLDRData",Source.PatternInfo::class.java,SourceLocale::class.java).apply { isAccessible=true }
        val type=Source::class.java.getDeclaredMethod("getCalendarTypeToUse",SourceLocale::class.java).apply { isAccessible=true }
        val keys=Source::class.java.getDeclaredField("cldrAvailableFormatKeys").apply { isAccessible=true }
        val ids=(SourceLocale.getAvailableLocales().map { it.toString() }+CalType.entries.map { "en_US@calendar=${it.getId()}" }+
            listOf("und","zz_ZZ","en_US@calendar=UNKNOWN","fa_IR","he_IL","ja_JP@calendar=japanese","th_TH","und@rg=irzzzz")).distinct()
        for(id in ids) for(includeStandard in listOf(false,true)) {
            val a=ctor.newInstance(); val b=DateTimePatternGeneratorCore(Source::class.java.desiredAssertionStatus())
            canonical.invoke(a); b.addCanonicalItems()
            val ai=Source.PatternInfo(); val bi=DateTimePatternGeneratorCore.PatternInfo()
            val locale=SourceLocale(id); val target=ULocale(id)
            assertEquals(type.invoke(a,locale),resources.calendarType(target),"type/$id")
            fun state(label: String) {
                assertEquals(a.getSkeletons(LinkedHashMap()).entries.map { it.key to it.value },b.getSkeletons(null).entries.map { it.key to it.value },"patterns/$label")
                assertEquals(a.getBaseSkeletons(null),b.getBaseSkeletons(null),"bases/$label")
                for(f in 0..15) {
                    assertEquals(a.getAppendItemFormat(f),b.getAppendItemFormat(f),"append/$label/$f")
                    for(w in Source.DisplayWidth.entries) assertEquals(a.getFieldDisplayName(f,w),b.getFieldDisplayName(f,DateTimePatternGeneratorCore.DisplayWidth.valueOf(w.name)),"name/$label/$f/$w")
                }
                assertEquals(ai.status,bi.status,label); assertEquals(ai.conflictingPattern,bi.conflictingPattern,label)
                for(key in keys.get(a) as Set<*>) assertTrue(b.isAvailableFormatSet(key as String),label)
            }
            if(includeStandard) {
                assertEquals(outcome { standard.invoke(a,ai,locale) },outcome { resources.addStandardPatterns(b,target,bi) },"standard/$id")
                state("standard/$id")
            }
            assertEquals(outcome { cldr.invoke(a,ai,locale) },outcome { resources.addCLDRData(b,target,bi) },"cldr/$id")
            state("cldr/$id")
            assertEquals(outcome { cldr.invoke(a,ai,SourceLocale.FRANCE) },outcome { resources.addCLDRData(b,ULocale.FRANCE,bi) },"repeat/$id")
            state("repeat/$id")
        }
    }
    @Test fun appendFieldKeyLookupPreservesFirstWildcardAndUnknownKeys() {
        for(key in listOf(null,"Era","Year","Quarter","Month","Week","*","Day-Of-Week","Day","Hour","Minute","Second","Timezone","day","zone",""))
            assertEquals(Source.getAppendFormatNumber(key),DateTimePatternResourceCore.appendFormatNumber(key),key)
    }
}
