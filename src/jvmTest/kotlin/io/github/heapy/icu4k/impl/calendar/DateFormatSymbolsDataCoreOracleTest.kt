@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateFormatSymbols as Source
import com.ibm.icu.impl.ICUResourceBundle as SourceBundle
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone
import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.Modifier
import kotlin.test.*

class DateFormatSymbolsDataCoreOracleTest {
    private val sinkClass=Class.forName("com.ibm.icu.text.DateFormatSymbols\$CalendarDataSink")
    private val fields=Source::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type==Array<String>::class.java }.onEach { it.isAccessible=true }
    private fun owner()=object: DateFormatSymbolsDataCore.Owner {
        override fun className()=Source::class.java.name
        override fun currentTimeMillis()=System.currentTimeMillis()
        override fun defaultZoneOffset(time: Long)=TimeZone.getDefault().getOffset(time)
    }
    private fun core()=DateFormatSymbolsDataCore(owner(),ICUResourceBundleReader.embeddedLoader,sinkClass.desiredAssertionStatus())
    private fun read(source: Source,name: String): Any?=Source::class.java.getDeclaredField(name).apply { isAccessible=true }.get(source)
    private fun compare(source: Source,target: DateFormatSymbolsDataCore,label: String) {
        val arrays=fields.associate { it.name to (it.get(source) as Array<*>?) }
        for((name,value) in arrays) assertEquals(value?.toList(),target.fields[name]?.toList(),"array/$label/$name")
        for((a,va) in arrays) for((b,vb) in arrays) assertEquals(va===vb,target.fields[a]===target.fields[b],"alias/$label/$a/$b")
        assertEquals(read(source,"requestedLocale")?.toString(),target.requestedLocale?.toString(),label)
        assertEquals(source.getLocale(SourceLocale.VALID_LOCALE)?.toString(),target.validLocale?.toString(),label)
        assertEquals(source.getLocale(SourceLocale.ACTUAL_LOCALE)?.toString(),target.actualLocale?.toString(),label)
        assertEquals(source.localPatternChars,target.localPatternChars,label); assertEquals(source.timeSeparatorString,target.timeSeparator,label)
        val capitalization=read(source,"capitalization") as Map<*,*>
        assertEquals(capitalization.entries.associate { it.key.toString() to (it.value as BooleanArray).toList() },target.capitalization!!.entries.associate { it.key.name to it.value.toList() },label)
        val names=capitalization.keys.toList()
        for(a in names) for(b in names) assertEquals(capitalization[a]===capitalization[b],target.capitalization!![io.github.heapy.icu4k.text.DateFormatSymbols.CapitalizationContextUsage.valueOf(a.toString())]===target.capitalization!![io.github.heapy.icu4k.text.DateFormatSymbols.CapitalizationContextUsage.valueOf(b.toString())],"capitalization aliases/$label/$a/$b")
    }
    @Test fun allLocaleArraysEraFallbacksCalendarAliasesAndNumberSystems() {
        val ctor=Source::class.java.getDeclaredConstructor(SourceLocale::class.java,SourceBundle::class.java,String::class.java).apply { isAccessible=true }
        val cases=SourceLocale.getAvailableLocales().map { it.toString() to com.ibm.icu.impl.CalendarUtil.getCalendarType(it) }+
            listOf("en_US","fr_FR","ja_JP","zh_CN","he_IL","fa_IR","ar_EG","ru_RU","fi_FI").flatMap { id -> CalType.entries.map { id to it.getId() } }+
            listOf("en_US@numbers=arab" to "gregorian","fa_IR@numbers=latn" to "persian","zz_ZZ" to "UNKNOWN","en_US" to "iso8601")
        for((id,type) in cases.distinct()) {
            val source=ctor.newInstance(SourceLocale(id),null,type)
            val target=core(); target.initialize(ULocale(id),null,type)
            compare(source,target,"$id/$type")
        }
    }
    private class Probe: Source(SourceLocale.US) {
        fun initialize(locale: SourceLocale?,type: String?) { super.initializeData(locale,null,type) }
    }
    private class SeparatorProbe: Source(SourceLocale.US) {
        val calls=ArrayList<String?>(); var missing=true; var broken=false
        override fun setTimeSeparatorString(value: String?) {
            calls.add(value)
            if(broken) error("setter")
            if(missing) { missing=false; throw java.util.MissingResourceException("setter","test","separator") }
            super.setTimeSeparatorString("override($value)")
        }
        fun reload() { super.initializeData(SourceLocale.US,null,"gregorian") }
    }
    @Test fun separatorSetterDispatchAndItsMissingResourceFallbackMatchSource() {
        val source=SeparatorProbe()
        val target=object: DateFormatSymbolsDataCore(owner(),ICUResourceBundleReader.embeddedLoader,sinkClass.desiredAssertionStatus()) {
            val calls=ArrayList<String?>(); var missing=true; var broken=false
            override fun setTimeSeparatorString(value: String?) {
                calls.add(value)
                if(broken) error("setter")
                if(missing) { missing=false; throw io.github.heapy.icu4k.util.MissingResourceException("setter","test","separator") }
                super.setTimeSeparatorString("override($value)")
            }
        }
        source.reload(); target.initialize(ULocale.US,null,"gregorian")
        assertEquals(listOf<String?>(":",":"),source.calls.toList()); assertEquals(source.calls,target.calls); compare(source,target,"setter fallback")
        source.calls.clear(); target.calls.clear(); source.broken=true; target.broken=true
        assertFailsWith<IllegalStateException> { source.reload() }
        assertFailsWith<IllegalStateException> { target.initialize(ULocale.US,null,"gregorian") }
        assertEquals(listOf<String?>(":"),source.calls.toList()); assertEquals(source.calls,target.calls); compare(source,target,"setter failure")
    }
    @Test fun repeatedInitializationRetainsSourceLeapMonthStateAndPartialWrites() {
        val source=Probe(); val target=core(); target.initialize(ULocale.US,null,"gregorian")
        for((id,type) in listOf("zh_CN" to "chinese","en_US" to "gregorian","ja_JP" to "japanese","fa_IR" to "UNKNOWN")) {
            source.initialize(SourceLocale(id),type); target.initialize(ULocale(id),null,type); compare(source,target,"repeat/$id/$type")
        }
        assertFailsWith<NullPointerException> { source.initialize(SourceLocale.US,null) }
        assertFailsWith<NullPointerException> { target.initialize(ULocale.US,null,null) }
        // The first weekday field is allocated before the missing source array fails.
        for(field in fields) assertEquals((field.get(source) as Array<*>?)?.toList(),target.fields[field.name]?.toList(),"partial/${field.name}")
    }
}
