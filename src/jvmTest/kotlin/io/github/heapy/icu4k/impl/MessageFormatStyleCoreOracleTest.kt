@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.text.MessageFormat as Source
import com.ibm.icu.text.NumberFormat as SourceNumber
import com.ibm.icu.text.DecimalFormat as SourceDecimal
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.calendar.*
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class MessageFormatStyleCoreOracleTest {
    private val create=Source::class.java.getDeclaredMethod("createAppropriateFormat",String::class.java,String::class.java).also { it.isAccessible=true }
    private fun outcome(block:()->Any?):Any?=try { block() } catch(e:Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException)e.targetException else e
        if(t is VirtualMachineError)throw t
        when(t) { is NullPointerException->"null"; is IndexOutOfBoundsException->"bounds"; else->t.javaClass.simpleName+":"+t.message }
    }
    internal class Owner(override val locale:ULocale) : MessageFormatStyleCore.Owner {
        override fun dateStyle(date:Boolean,style:Int,locale:ULocale?):Any=SimpleDateFormat.normalDateStyle(date,style,locale)
        override fun dateSkeleton(skeleton:String,locale:ULocale?):Any=SimpleDateFormat(SimpleDateFormat.bestPattern(skeleton,locale),locale)
        override fun datePattern(pattern:String,locale:ULocale?):Any=SimpleDateFormat(pattern,locale)
    }
    private fun parsed(value:Any?):Any?=when(value) { is java.util.Date->value.time; is Number->value.toString(); else->value }
    private fun compare(a:java.text.Format,b:java.text.Format,label:String) {
        fun kind(value:Any):String=when(value) {
            is SimpleDateFormat->"SimpleDateFormat"
            is io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormat->"LocalizedNumberFormatterAsFormat"
            else->value.javaClass.simpleName
        }
        assertEquals(kind(a),kind(b),label)
        if(a is com.ibm.icu.text.SimpleDateFormat && b is SimpleDateFormat) {
            a.timeZone=com.ibm.icu.util.TimeZone.getTimeZone("Etc/UTC"); b.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
            a.set2DigitYearStart(java.util.Date(0)); b.state.setCenturyStart(Date(0))
            assertEquals(a.toPattern(),b.state.pattern,label)
        }
        for(value in listOf<Number>(0L,12L,-1234L,12.34,Double.NaN,Double.POSITIVE_INFINITY)) {
            val at=outcome { a.format(value) }; val bt=outcome { b.format(value) }
            assertEquals(at,bt,label+"/$value")
            if(at is String) {
                val ap=java.text.ParsePosition(0); val bp=java.text.ParsePosition(0)
                assertEquals(outcome { parsed(a.parseObject(at,ap)) },outcome { parsed(b.parseObject(at,bp)) },label+"/parse/$at")
                assertEquals(listOf(ap.index,ap.errorIndex),listOf(bp.index,bp.errorIndex),label+"/positions/$at")
            }
        }
    }
    @Test fun everyStyleUsesRealCommonNumericAndDateEngines() {
        for(id in listOf("en_US","ru","fr","ar_EG","ja","he_IL@calendar=hebrew")) {
            val source=Source("",SourceLocale(id)); val core=MessageFormatStyleCore(Owner(ULocale(id)))
            val groups=listOf(
                "number" to listOf("","currency","percent","integer"," INTEGER ","\u200finteger\t","0.00","0000"," ::precision-integer","::currency/USD precision-integer","::bad-token"),
                "date" to listOf("","short","medium","long","full","\u200e ::yMd",":: yMMMMd","yyyy-MM-dd HH:mm:ss","bad j"),
                "time" to listOf("","short","medium","long","full","::Hm","HH:mm:ss.SSS"),
                "spellout" to listOf("","%spellout-numbering","%invalid"," \t%invalid\u0000 ","\u200e%spellout-numbering"),
                "ordinal" to listOf("","%digits-ordinal","%invalid"),
                "duration" to listOf("","%duration","%invalid"))
            for((type,styles) in groups) for(style in styles) {
                var a:java.text.Format?=null; var b:java.text.Format?=null
                val label="$id/$type/$style"
                assertEquals(outcome { a=create.invoke(source,type,style) as java.text.Format; "ok" },outcome { b=core.create(type,style) as java.text.Format; "ok" },label)
                if(a!=null && b!=null)compare(a!!,b!!,label)
            }
            for(type in listOf(null," NUMBER ","\u200fDATE\t","unknown","\u00a0number")) {
                assertEquals(outcome { create.invoke(source,type,null); "ok" },outcome { core.create(type,null); "ok" },"$id/$type/null")
            }
        }
    }
    @Test fun keywordNumberStylesUseRegisteredFactoriesAndPatternsBypassThem() {
        var acount=0; var bcount=0
        val ak=SourceNumber.registerFactory(object:SourceNumber.SimpleNumberFormatFactory(SourceLocale.US,false) {
            override fun createFormat(loc:SourceLocale?,formatType:Int):SourceNumber? { acount++; return SourceDecimal("'factory'0") }
        })
        val bk=NumberFormat.registerFactory(object:SimpleNumberFormatFactory(ULocale.US,false) {
            override fun createFormat(loc:ULocale?,formatType:Int):NumberFormat? { bcount++; return DecimalFormat("'factory'0") }
        })
        try {
            val source=Source("",SourceLocale.US); val core=MessageFormatStyleCore(Owner(ULocale.US))
            for(style in listOf("","integer","percent","currency","0000","::precision-integer")) {
                val beforeA=acount; val beforeB=bcount
                val a=create.invoke(source,"number",style) as java.text.Format
                val b=core.create("number",style) as java.text.Format
                assertEquals(acount>beforeA,bcount>beforeB,style)
                assertEquals(style in listOf("","integer","percent","currency"),acount>beforeA,style)
                assertEquals(a.format(12L),b.format(12L),style)
            }
        } finally { SourceNumber.unregister(ak); NumberFormat.unregister(bk) }
    }
}
