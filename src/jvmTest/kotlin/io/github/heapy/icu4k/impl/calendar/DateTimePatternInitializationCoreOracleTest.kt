@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateTimePatternGenerator as Source
import com.ibm.icu.util.Calendar
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.CalType
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateTimePatternInitializationCoreOracleTest {
    private val initialization=DateTimePatternInitializationCore(ICUResourceBundleReader.embeddedLoader)
    private fun compare(a: Source,b: DateTimePatternGeneratorCore,id: String) {
        assertEquals(a.getSkeletons(LinkedHashMap()).entries.map { it.key to it.value },b.getSkeletons(null).entries.map { it.key to it.value },"patterns/$id")
        assertEquals(a.getBaseSkeletons(null),b.getBaseSkeletons(null),id)
        assertEquals(a.decimal,b.getDecimal(),"decimal/$id")
        assertEquals(a.defaultHourFormatChar,b.getDefaultHourFormatChar(),"hour/$id")
        assertEquals(a.isFrozen,b.isFrozen(),id)
        for(style in 0..3) assertEquals(a.getDateTimeFormat(style),b.getDateTimeFormat(style),"glue/$id/$style")
        for(field in 0..15) {
            assertEquals(a.getAppendItemFormat(field),b.getAppendItemFormat(field),"append/$id/$field")
            for(width in Source.DisplayWidth.entries) assertEquals(a.getFieldDisplayName(field,width),b.getFieldDisplayName(field,DateTimePatternGeneratorCore.DisplayWidth.entries[width.ordinal]),"name/$id/$field/$width")
        }
        for(request in listOf("yMd","yMMMMEEEEd","yMMMMdHmsSSS","jmm","Cmm","jjjjmm","JJmmss","yQQQQ","msSSSS","Ehmv")) {
            for(option in listOf(0,0xffff)) assertEquals(a.getBestPattern(request,option),b.getBestPattern(request,option),"best/$id/$request/$option")
        }
    }
    @Test fun noStandardFactoryMatchesFullLocaleInitializationAndSearch() {
        val ids=(SourceLocale.getAvailableLocales().map { it.toString() }+CalType.entries.map { "en_US@calendar=${it.getId()}" }+
            listOf("en_US@hours=h11","en_US@hours=h23","en_GB@hours=h24","en_US@rg=gbzzzz","en_US@numbers=arab","fa_IR@numbers=latn","en_US@calendar=UNKNOWN")).distinct()
        for(id in ids) compare(Source.getInstanceNoStdPat(SourceLocale(id)),initialization.noStandardPatterns(ULocale(id),Source::class.java.desiredAssertionStatus()),id)
    }
    @Test fun normalInitializationUsesActualCalendarOwnerForStyleGlue() {
        val ctor=Source::class.java.getDeclaredConstructor().apply { isAccessible=true }
        val initialize=Source::class.java.getDeclaredMethod("initData",SourceLocale::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        for(id in listOf("root","en_US","en_US@hours=h23","en_US@rg=gbzzzz","fa_IR","fr_FR","ar_EG","he_IL","ja_JP@calendar=japanese","th_TH","en_ZZ")) {
            val a=ctor.newInstance(); initialize.invoke(a,SourceLocale(id),false)
            val b=DateTimePatternGeneratorCore(Source::class.java.desiredAssertionStatus()); var constructions=0; val styles=ArrayList<Int>()
            initialization.initialize(b,ULocale(id)) { locale ->
                constructions++
                val sourceLocale=SourceLocale(locale.toString()); val calendar=Calendar.getInstance(sourceLocale)
                DateTimePatternInitializationCore.CalendarPatterns { style -> styles.add(style); Calendar.getDateAtTimePattern(calendar,sourceLocale,style) }
            }
            assertEquals(1,constructions,id); assertEquals(listOf(0,1,2,3),styles,id)
            compare(a,b,id)
        }
    }
}
