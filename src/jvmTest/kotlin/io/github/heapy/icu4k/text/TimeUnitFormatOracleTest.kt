@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.TimeUnitFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class TimeUnitFormatOracleTest {
    private fun parsed(a:Source,b:TimeUnitFormat,text:String?,start:Int=0) {
        val ap=java.text.ParsePosition(start);val bp=ParsePosition(start)
        val x=a.parseObject(text,ap);val y=b.parseObject(text,bp)
        assertEquals(x?.number?.toString(),y?.getNumber()?.toString(),"$text/$start")
        assertEquals(x?.number?.javaClass?.simpleName,y?.getNumber()?.javaClass?.simpleName,"$text/$start/type")
        assertEquals(x?.timeUnit?.toString(),y?.getTimeUnit()?.toString(),"$text/$start/unit")
        assertEquals(ap.index,bp.index,"$text/$start/index");assertEquals(ap.errorIndex,bp.errorIndex,"$text/$start/error")
    }
    @Test fun bothStylesAndPluralCountsUseTheSameLongestParsingMatch() {
        for(id in listOf("en_US","fr","de","ru","pl","ar","fa","he","ja","zh","hi","fi","bn","th","my","am","root","zz")) for(style in 0..1) {
            val a=Source(SourceLocale(id),style);val b=TimeUnitFormat(ULocale(id),style)
            for(unit in TimeUnit.values()) for(n in listOf(0,1,2,3,5,21,1.5)) {
                val au=com.ibm.icu.util.TimeUnit.values().single { it.toString()==unit.toString() }
                val expected=a.format(com.ibm.icu.util.TimeUnitAmount(n,au));val actual=b.format(TimeUnitAmount(n,unit))
                assertEquals(expected,actual,"$id/$style/$unit/$n")
                parsed(a,b,expected);parsed(a,b,"prefix:$expected trailing",7)
            }
            for(text in listOf("","bad","1 s","2 min","1 m","2 h","3 d","4 w","5 y","one hour","1")) parsed(a,b,text)
            val ac=a.clone();val bc=b.clone();parsed(ac,bc,"2 h")
        }
    }
    @Test fun localeAndNumberFormatMutationPreserveSourceLazyParserState() {
        val a=Source(SourceLocale.US);val b=TimeUnitFormat(ULocale.US)
        for(id in listOf("fr","ru","ja")) {
            a.setLocale(SourceLocale(id));b.setLocale(ULocale(id))
            assertEquals(a.locale.toString(),b.getLocale().toString())
            assertEquals(a.format(com.ibm.icu.util.TimeUnitAmount(2,com.ibm.icu.util.TimeUnit.HOUR)),b.format(TimeUnitAmount(2,TimeUnit.HOUR)))
            parsed(a,b,"2 hours");parsed(a,b,"2 h")
        }
        val an=com.ibm.icu.text.DecimalFormat("0.000");val bn=DecimalFormat("0.000")
        assertSame(a,a.setNumberFormat(an));assertSame(b,b.setNumberFormat(bn))
        assertEquals(a.format(com.ibm.icu.util.TimeUnitAmount(2.5,com.ibm.icu.util.TimeUnit.HOUR)),b.format(TimeUnitAmount(2.5,TimeUnit.HOUR)))
        an.maximumFractionDigits=1;bn.setMaximumFractionDigits(1)
        parsed(a,b,"2.125 h")
        assertEquals(a.format(com.ibm.icu.util.TimeUnitAmount(2.125,com.ibm.icu.util.TimeUnit.HOUR)),b.format(TimeUnitAmount(2.125,TimeUnit.HOUR)))
        a.setNumberFormat(null);b.setNumberFormat(null);parsed(a,b,"2 h")
        val freshA=Source(SourceLocale.FRANCE);val freshB=TimeUnitFormat(ULocale.FRANCE)
        freshA.setNumberFormat(null);freshB.setNumberFormat(null);parsed(freshA,freshB,"2 heures")
    }
    @Test fun sourcePatternTablesAndPrivateLocaleAfterLazySetupMatch() {
        fun field(value:Any,name:String):Any?=value.javaClass.getDeclaredField(name).also { it.isAccessible=true }.get(value)
        fun tables(value:Any):Map<String,Map<String,List<String?>>> = (field(value,"timeUnitToCountToPatterns") as Map<*,*>).entries.associate { (unit,patterns) ->
            unit.toString() to (patterns as Map<String,Array<*>>).mapValues { (_,pair) -> pair.map { it?.javaClass?.getMethod("toPattern")?.invoke(it) as String? } }
        }
        for(id in listOf("en_US","fr","ar","ru","ja","root","zz")) {
            val a=Source(SourceLocale(id));val b=TimeUnitFormat(ULocale(id))
            assertNull(field(b,"locale"));parsed(a,b,"1 h")
            assertEquals(field(a,"locale").toString(),field(b,"locale").toString());assertEquals(tables(a),tables(b),id)
        }
    }
    @Test fun threeEntryCacheDoesNotPromoteHitsAndMutableNumberFormatsReachNewUnits() {
        val a=Source(SourceLocale.US);val b=TimeUnitFormat(ULocale.US)
        val ae=mutableListOf<String>();val be=mutableListOf<String>()
        val an=object:com.ibm.icu.text.DecimalFormat("0.000") {
            override fun toNumberFormatter():com.ibm.icu.number.LocalizedNumberFormatter { ae.add("formatter");return super.toNumberFormatter() }
        }
        val bn=object:DecimalFormat("0.000") {
            override fun toNumberFormatter():io.github.heapy.icu4k.number.LocalizedNumberFormatter { be.add("formatter");return super.toNumberFormatter() }
        }
        a.setNumberFormat(an);b.setNumberFormat(bn)
        for((index,unit) in listOf(TimeUnit.HOUR,TimeUnit.MINUTE,TimeUnit.SECOND,TimeUnit.HOUR,TimeUnit.DAY,TimeUnit.HOUR,TimeUnit.MINUTE,TimeUnit.WEEK).withIndex()) {
            if(index==3) { an.maximumFractionDigits=0;bn.setMaximumFractionDigits(0) }
            val au=com.ibm.icu.util.TimeUnit.values().single { it.toString()==unit.toString() }
            assertEquals(a.format(com.ibm.icu.util.TimeUnitAmount(2.5,au)),b.format(TimeUnitAmount(2.5,unit)))
            assertEquals(ae,be,"$index/$unit")
        }
        assertEquals(7,ae.size)
    }
}
