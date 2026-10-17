@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class TimeUnitFormatTest {
    @Test fun localizedParsingBothStylesPartialMatchesAndFallbackSourceGoldens() {
        data class Case(val id:String,val text:String,val number:Double?,val unit:TimeUnit?,val index:Int)
        for(c in listOf(Case("en_US","1 hour",1.0,TimeUnit.HOUR,6),Case("en_US","2 min",2.0,TimeUnit.MINUTE,5),Case("en_US","2 h",null,null,0),Case("fr","2 heures",2.0,TimeUnit.HOUR,3),Case("fr","1 m",1.0,TimeUnit.MONTH,3),Case("ar","2 min",2.0,TimeUnit.MONTH,3),Case("ar","٢ ساعة",2.0,TimeUnit.HOUR,6),Case("ja","1 時間",1.0,TimeUnit.HOUR,4))) {
            val f=TimeUnitFormat(ULocale(c.id));val p=ParsePosition(0)
            val value=f.parseObject(c.text,p)
            assertEquals(c.number,value?.getNumber()?.toDouble(),c.toString());assertSame(c.unit,value?.getTimeUnit(),c.toString())
            assertEquals(c.index,p.index,c.toString());assertEquals(if(c.number==null) 0 else -1,p.errorIndex)
        }
    }
    @Test fun privateParsingLocaleAndFormattingLocaleMutationMatchSource() {
        val f=TimeUnitFormat(ULocale.US)
        f.setLocale(ULocale.FRANCE)
        assertEquals(ULocale.FRANCE,f.getLocale())
        assertEquals("2 hours",f.format(TimeUnitAmount(2,TimeUnit.HOUR)))
        val parsed=f.parseObject("2 hours",ParsePosition(0))
        assertEquals(2.0,parsed!!.getNumber().toDouble());assertEquals(ULocale.US,f.getLocale())
        f.setLocale(ULocale.JAPAN)
        assertEquals("2 hours",f.format(TimeUnitAmount(2,TimeUnit.HOUR)))
        val clone=f.clone();assertNotSame(f,clone)
        assertEquals(f.format(TimeUnitAmount(2,TimeUnit.HOUR)),clone.format(TimeUnitAmount(2,TimeUnit.HOUR)))
        assertFailsWith<IllegalArgumentException> { TimeUnitFormat(ULocale.US,2) }
    }
    @Test fun mutableNumberFormatAndNativeCloneHookRetainSubtype() {
        val f=TimeUnitFormat(ULocale.US)
        val nf=DecimalFormat("0.000",DecimalFormatSymbols(ULocale.US));f.setNumberFormat(nf)
        assertEquals("2.500 hours",f.format(TimeUnitAmount(2.5,TimeUnit.HOUR)))
        nf.setMaximumFractionDigits(0)
        // The existing unit formatter remains cached; a different unit observes the change.
        assertEquals("2.500 hours",f.format(TimeUnitAmount(2.5,TimeUnit.HOUR)))
        assertEquals("2 minutes",f.format(TimeUnitAmount(2.5,TimeUnit.MINUTE)))
        class Derived : TimeUnitFormat {
            val marker:String
            constructor():super(ULocale.US) { marker="subtype" }
            private constructor(other:Derived):super(other,Unit) { marker=other.marker }
            override fun cloneNative():MeasureFormat=Derived(this)
        }
        val d=Derived();d.parseObject("1 hour",ParsePosition(0))
        val copy=d.clone();assertTrue(copy is Derived);assertEquals("subtype",copy.marker)
        assertEquals(d.format(TimeUnitAmount(1,TimeUnit.HOUR)),copy.format(TimeUnitAmount(1,TimeUnit.HOUR)))
    }
}
