@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateFormatSymbols as Source
import com.ibm.icu.impl.CacheBase as SourceCache
import com.ibm.icu.impl.SoftCache as SourceSoftCache
import com.ibm.icu.impl.ICUResourceBundle as SourceBundle
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone
import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.test.*

class DateFormatSymbolsFactoryCoreOracleTest {
    private fun blank()=DateFormatSymbolsStateCore(DateFormatSymbolsDataCore(object: DateFormatSymbolsDataCore.Owner {
        override fun className()=Source::class.java.name
        override fun currentTimeMillis()=System.currentTimeMillis()
        override fun defaultZoneOffset(time: Long)=TimeZone.getDefault().getOffset(time)
    },ICUResourceBundleReader.embeddedLoader,Class.forName("com.ibm.icu.text.DateFormatSymbols\$CalendarDataSink").desiredAssertionStatus()))
    private class A(private val creator: (String,SourceLocale?)->Source): SourceCache<String,Source,SourceLocale?>() {
        val trace=ArrayList<String>(); var mode=0; var failCreate=false; var intervene: (()->Unit)?=null
        val templates=ArrayList<Source>() // Keep soft-cache publications alive while asserting identity.
        private val soft=object: SourceSoftCache<String,Source,SourceLocale?>() {
            override fun createInstance(key: String,data: SourceLocale?): Source {
                trace.add("create:$key:$data"); intervene?.let { intervene=null; it() }; if(failCreate) error("create"); return creator(key,data).also { templates.add(it) }
            }
        }
        override fun getInstance(key: String,data: SourceLocale?): Source? {
            trace.add("get:$key:$data"); if(mode==1) error("read"); if(mode==2) return null; return soft.getInstance(key,data)
        }
        override fun createInstance(key: String,data: SourceLocale?): Source=error("unexpected create")
    }
    private class B: CacheBase<String,DateFormatSymbolsStateCore,ULocale?>() {
        lateinit var factory: DateFormatSymbolsFactoryCore
        val trace=ArrayList<String>(); var mode=0; var failCreate=false; var intervene: (()->Unit)?=null
        val templates=ArrayList<DateFormatSymbolsStateCore>()
        private val soft=object: SoftCache<String,DateFormatSymbolsStateCore,ULocale?>() {
            override fun createInstance(key: String,data: ULocale?): DateFormatSymbolsStateCore {
                trace.add("create:$key:$data"); intervene?.let { intervene=null; it() }; if(failCreate) error("create"); return factory.createTemplate(key,data).also { templates.add(it) }
            }
        }
        override fun getInstance(key: String,data: ULocale?): DateFormatSymbolsStateCore? {
            trace.add("get:$key:$data"); if(mode==1) error("read"); if(mode==2) return null; return soft.getInstance(key,data)
        }
        override fun createInstance(key: String,data: ULocale?): DateFormatSymbolsStateCore=error("unexpected create")
    }
    private fun invoke(action: ()->Any?): Any? = try { action() } catch(error: InvocationTargetException) { throw error.targetException }
    private fun isolated(block: (A,B,DateFormatSymbolsFactoryCore)->Unit) {
        val field=Source::class.java.getDeclaredField("DFSCACHE").apply { isAccessible=true }
        val old=field.get(null)!!
        val fresh=old.javaClass.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
        val create=old.javaClass.getDeclaredMethod("createInstance",String::class.java,SourceLocale::class.java).apply { isAccessible=true }
        val a=A { key,data -> invoke { create.invoke(fresh,key,data) } as Source }; val b=B()
        val factory=DateFormatSymbolsFactoryCore({ blank() },defaultFormatLocale={ ULocale(SourceLocale.getDefault(SourceLocale.Category.FORMAT).toString()) },cache=b)
        b.factory=factory; field.set(null,a)
        try { block(a,b,factory) } finally { field.set(null,old) }
    }
    private val arrays=Source::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type==Array<String>::class.java }.onEach { it.isAccessible=true }
    private fun state(source: Source,target: DateFormatSymbolsStateCore) {
        for(field in arrays) assertEquals((field.get(source) as Array<*>?)?.toList(),target.data.fields[field.name]?.toList(),field.name)
        for(a in arrays) for(b in arrays) assertEquals(a.get(source)===b.get(source),target.data.fields[a.name]===target.data.fields[b.name],"alias/${a.name}/${b.name}")
        val requested=Source::class.java.getDeclaredField("requestedLocale").apply { isAccessible=true }.get(source)
        assertEquals(requested.toString(),target.data.requestedLocale.toString())
        assertEquals(source.hashCode(),target.hashCodeState()); assertEquals(source.timeSeparatorString,target.getTimeSeparatorString())
    }
    private open class Probe: Source(SourceLocale.US) {
        fun load(locale: SourceLocale?,type: String?) { super.initializeData(locale,type) }
    }
    @Test fun localeFactoriesKeysTemplatesAndMutationAliasesMatchSource()=isolated { a,b,factory ->
        for(id in listOf("en_US@hours=h23","en_US","en_US@numbers=arab","en_US@numbers=latn","en_US@calendar=chinese","en_US@calendar=chinese;currency=eur","fr_FR","fa_IR","en_US@calendar=UNKNOWN","ja_JP_TRADITIONAL","th_TH_TRADITIONAL","en_US_POSIX","en_US@calendar=ISO8601","en_US@numbers=ARAB","en_US@rg=gbzzzz")) {
            val source=Source.getInstance(SourceLocale(id)); val target=factory.getInstance(ULocale(id)); state(source,target)
            val source2=Source.getInstance(SourceLocale(id)); val target2=factory.getInstance(ULocale(id))
            assertNotSame(source,source2); assertNotSame(target,target2)
            val months=arrays.single { it.name=="months" }
            assertSame(months.get(source),months.get(source2)); assertSame(target.data.fields["months"],target2.data.fields["months"])
            state(source2,target2); assertEquals(a.trace,b.trace)
        }
        state(Source.getInstance(),factory.getInstance()); assertEquals(a.trace,b.trace)
    }
    @Test fun protectedTypesUncachedBundlesAndClassMappingMatchSource()=isolated { a,b,factory ->
        val source=Probe(); val target=factory.getInstance(ULocale.US)
        for(type in listOf(null,"gregorian","chinese+tail","UNKNOWN","persian")) {
            source.load(SourceLocale.US,type); factory.initializeData(target,ULocale.US,type); state(source,target); assertEquals(a.trace,b.trace)
        }
        val trace=a.trace.toList()
        val direct=Source(null as java.util.ResourceBundle?,SourceLocale("en_US@calendar=chinese"))
        state(direct,factory.fromBundle(ULocale("en_US@calendar=chinese"),null)); assertEquals(trace,a.trace); assertEquals(a.trace,b.trace)
        val classes=listOf(com.ibm.icu.util.GregorianCalendar::class.java,com.ibm.icu.util.JapaneseCalendar::class.java,com.ibm.icu.util.BuddhistCalendar::class.java,com.ibm.icu.util.TaiwanCalendar::class.java,com.ibm.icu.util.PersianCalendar::class.java,com.ibm.icu.util.IslamicCalendar::class.java,com.ibm.icu.util.HebrewCalendar::class.java,com.ibm.icu.util.ChineseCalendar::class.java,com.ibm.icu.util.IndianCalendar::class.java,com.ibm.icu.util.CopticCalendar::class.java,com.ibm.icu.util.EthiopicCalendar::class.java,com.ibm.icu.util.DangiCalendar::class.java)
        for(clazz in classes) { state(Source(clazz,SourceLocale.US),factory.forCalendarClassName(ULocale.US,clazz.name)); assertEquals(a.trace,b.trace) }
        assertEquals(Source.getAvailableULocales().map { it.toString() },factory.getAvailableULocales()!!.map { it.toString() })
    }
    @Test fun cachedLeapChangesAndFirstRequestedLocalePersistLikeSource()=isolated { a,b,factory ->
        val id="zh_CN@calendar=chinese;hours=h23"
        val source=Source.getInstance(SourceLocale(id)); val target=factory.getInstance(ULocale(id))
        source.setLeapMonthPattern("cached",0,0); target.setLeapMonthPattern("cached",0,0)
        val source2=Source.getInstance(SourceLocale("zh_CN@calendar=chinese")); val target2=factory.getInstance(ULocale("zh_CN@calendar=chinese"))
        assertEquals("cached",source2.getLeapMonthPattern(0,0)); state(source2,target2); assertEquals(a.trace,b.trace)
    }
    @Test fun failedAndNullCacheReadsRetainTargetAndRetry()=isolated { a,b,factory ->
        val source=Probe(); val target=factory.getInstance(ULocale.US)
        a.mode=1; b.mode=1
        assertFailsWith<IllegalStateException> { source.load(SourceLocale.FRANCE,"gregorian") }
        assertFailsWith<IllegalStateException> { factory.initializeData(target,ULocale.FRANCE,"gregorian") }; state(source,target)
        a.mode=2; b.mode=2
        assertFailsWith<NullPointerException> { source.load(SourceLocale.FRANCE,"gregorian") }
        assertFailsWith<NullPointerException> { factory.initializeData(target,ULocale.FRANCE,"gregorian") }; state(source,target)
        a.mode=0; b.mode=0
        source.load(SourceLocale.FRANCE,"gregorian"); factory.initializeData(target,ULocale.FRANCE,"gregorian"); state(source,target); assertEquals(a.trace,b.trace)
        a.failCreate=true; b.failCreate=true
        assertFailsWith<IllegalStateException> { source.load(SourceLocale.JAPAN,"chinese") }
        assertFailsWith<IllegalStateException> { factory.initializeData(target,ULocale.JAPAN,"chinese") }; state(source,target); assertEquals(a.trace,b.trace)
        a.failCreate=false; b.failCreate=false
        source.load(SourceLocale.JAPAN,"chinese"); factory.initializeData(target,ULocale.JAPAN,"chinese"); state(source,target); assertEquals(a.trace,b.trace)
        assertFailsWith<NullPointerException> { Source.getInstance(null as SourceLocale?) }; assertFailsWith<NullPointerException> { factory.getInstance(null) }; assertEquals(a.trace,b.trace)
    }
    @Test fun interveningSoftCachePublicationWinsForBothResults()=isolated { a,b,factory ->
        var nestedSource: Source?=null; var nestedTarget: DateFormatSymbolsStateCore?=null
        a.intervene={ nestedSource=Source.getInstance(SourceLocale("en_US@hours=h23")) }
        b.intervene={ nestedTarget=factory.getInstance(ULocale("en_US@hours=h23")) }
        val source=Source.getInstance(SourceLocale("en_US@hours=h12")); val target=factory.getInstance(ULocale("en_US@hours=h12"))
        state(source,target); state(nestedSource!!,nestedTarget!!); assertEquals(a.trace,b.trace)
        assertEquals("en_US@hours=h23",target.data.requestedLocale.toString())
        assertSame(target.data.fields["months"],nestedTarget!!.data.fields["months"])
    }
}
