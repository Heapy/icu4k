package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.impl.ICUData as SourceData
import com.ibm.icu.impl.ICUResourceBundle as SourceBundle
import com.ibm.icu.impl.UResource as SourceResource
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.UResourceBundle as SourceResources
import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class CalendarSymbolSinkOracleTest {
    private val clazz=Class.forName("com.ibm.icu.text.DateFormatSymbols\$CalendarDataSink")
    private fun read(obj: Any,name: String): Any?=obj.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(obj)
    private fun snapshot(sink: Any,arrays: List<Pair<String,Array<String>>>,maps: List<Pair<String,Map<String,String>>>): List<Any?> = listOf(
        arrays.map { it.first to it.second.toList() },maps.map { it.first to it.second.toMap() },
        (read(sink,"aliasPathPairs") as List<*>).toList(),read(sink,"currentCalendarType"),read(sink,"nextCalendarType"),
        (read(sink,"resourcesToVisit") as Set<*>?)?.toSet(),read(sink,"aliasRelativePath"),
        arrays.flatMap { a -> arrays.filter { b -> a.second===b.second }.map { a.first to it.first } },
        maps.flatMap { a -> maps.filter { b -> a.second===b.second }.map { a.first to it.first } },
    )
    @Suppress("UNCHECKED_CAST")
    @Test fun everyFallbackStepMatchesSourceMapsAliasIdentityAndVisitFilters() {
        val constructor=clazz.getDeclaredConstructor().apply { isAccessible=true }
        val pre=clazz.getDeclaredMethod("preEnumerate",String::class.java).apply { isAccessible=true }
        val all=clazz.getDeclaredMethod("visitAllResources").apply { isAccessible=true }
        for(id in listOf("root","en_US","zh_CN","ja_JP","ru_RU","fi_FI","he_IL","fa_IR")) for(type in CalType.entries.map { it.getId() }+"UNKNOWN") {
            val a=constructor.newInstance() as SourceResource.Sink
            val b=CalendarSymbolSink(clazz.desiredAssertionStatus())
            val source=SourceResources.getBundleInstance(SourceData.ICU_BASE_NAME,SourceLocale(id)) as SourceBundle
            val target=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,ULocale(id),ICUResourceBundleReader.embeddedLoader) as ICUResourceBundle
            val expected=ArrayList<List<Any?>>()
            var calendar: String?=type
            while(calendar!=null) {
                val data=source.findWithFallback("calendar/$calendar")
                if(data==null) { calendar="gregorian"; all.invoke(a); continue }
                pre.invoke(a,calendar)
                data.getAllItemsWithFallback("",object: SourceResource.Sink() {
                    override fun put(key: SourceResource.Key,value: SourceResource.Value,noFallback: Boolean) {
                        a.put(key,value,noFallback)
                        expected.add(snapshot(a,(read(a,"arrays") as Map<String,Array<String>>).entries.map { it.key to it.value },(read(a,"maps") as Map<String,Map<String,String>>).entries.map { it.key to it.value }))
                    }
                })
                if(calendar=="gregorian") break
                calendar=read(a,"nextCalendarType") as String?
                if(calendar==null) { calendar="gregorian"; all.invoke(a) }
            }
            val actual=ArrayList<List<Any?>>()
            calendar=type
            while(calendar!=null) {
                val data=target.findWithFallback("calendar/$calendar")
                if(data==null) { calendar="gregorian"; b.visitAllResources(); continue }
                b.preEnumerate(calendar)
                data.getAllItemsWithFallback("",object: UResource.Sink() {
                    override fun put(key: UResource.Key,value: UResource.Value,noFallback: Boolean) {
                        b.put(key,value,noFallback); actual.add(snapshot(b,b.arrays.toList(),b.maps.map { it.first to it.second }))
                    }
                })
                if(calendar=="gregorian") break
                calendar=b.nextCalendarType
                if(calendar==null) { calendar="gregorian"; b.visitAllResources() }
            }
            assertEquals(expected,actual,"$id/$type")
        }
    }
}
