@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateTimePatternGenerator as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateTimePatternHourDataCoreOracleTest {
    @Test fun resourceHourCyclesMatchEveryLocaleAndRegionWithOverrides() {
        val data=DateTimePatternHourDataCore(ICUResourceBundleReader.embeddedLoader)
        val method=Source::class.java.getDeclaredMethod("getAllowedHourFormats",SourceLocale::class.java).apply { isAccessible=true }
        val allowed=Source::class.java.getDeclaredField("allowedHourFormats").apply { isAccessible=true }
        val entries=Source::class.java.getDeclaredField("LOCALE_TO_ALLOWED_HOUR").apply { isAccessible=true }.get(null) as Map<*,*>
        val tableLocales=entries.keys.map { val id=it as String; if('_' in id) id else "und_$id" }
        val locales=(SourceLocale.getAvailableLocales().map { it.toString() }+SourceLocale.getISOCountries().map { "und_$it" }+tableLocales+
            listOf("","und","root","zz_ZZ","und_001","en_BU","en_SU","en_999","af_ZA","en_US@rg=gbzzzz","en_US@sd=gbeng","und@rg=irzzzz")).distinct()
        val source=Source.getEmptyInstance(); val core=DateTimePatternGeneratorCore.empty()
        for(id in locales) for(hours in listOf(null,"h11","h12","h23","h24","unknown","H12")) {
            val name=id+if(hours==null) "" else if('@' in id) ";hours=$hours" else "@hours=$hours"
            method.invoke(source,SourceLocale(name)); core.initializeHourFormats(ULocale(name),data)
            assertEquals(source.defaultHourFormatChar,core.getDefaultHourFormatChar(),name)
            val actual=data.get(ULocale(name))
            assertEquals((allowed.get(source) as Array<*>).toList(),actual.allowed.toList(),name)
            assertEquals(source.getBestPattern("Cmm"),core.getBestPattern("Cmm"),name)
            assertEquals(source.getBestPattern("jmm"),core.getBestPattern("jmm"),name)
        }
    }
}
