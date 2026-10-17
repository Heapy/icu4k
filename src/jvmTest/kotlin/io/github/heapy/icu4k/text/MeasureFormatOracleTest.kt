@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.MeasureFormat as Source
import com.ibm.icu.util.Measure as SourceMeasure
import com.ibm.icu.util.MeasureUnit as SourceUnit
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.impl.DontCareFieldPosition
import java.text.FieldPosition
import kotlin.test.*

class MeasureFormatOracleTest {
    private fun sourceUnit(unit: MeasureUnit): SourceUnit = if(unit is Currency) com.ibm.icu.util.Currency.getInstance(unit.getCurrencyCode()) else SourceUnit.forIdentifier(unit.getIdentifier())
    private fun sourceMeasures(values: List<Measure>) = values.map { SourceMeasure(it.getNumber(),sourceUnit(it.getUnit())) }.toTypedArray()
    private fun outcome(action:()->Any?):String=try { "ok:${action()}" }catch(e:Exception){ when(e){is NullPointerException->"null";is IndexOutOfBoundsException->"bounds";else->e.javaClass.simpleName+":"+e.message?.replace("com.ibm.icu.text.NumberFormat\$Field","NumberField")?.replace("io.github.heapy.icu4k.text.NumberFormatField","NumberField")} }
    private fun compare(a:Source,b:MeasureFormat,values:List<Measure>,label:String) {
        val source=sourceMeasures(values);val measures=values.toTypedArray()
        assertEquals(outcome { a.formatMeasures(*source) },outcome { b.formatMeasures(*measures) },"fast/$label")
        val fields=listOf(null to null,com.ibm.icu.text.NumberFormat.Field.INTEGER to NumberFormat.Field.INTEGER,com.ibm.icu.text.NumberFormat.Field.FRACTION to NumberFormat.Field.FRACTION,com.ibm.icu.text.NumberFormat.Field.MEASURE_UNIT to NumberFormat.Field.MEASURE_UNIT,com.ibm.icu.text.NumberFormat.Field.CURRENCY to NumberFormat.Field.CURRENCY,com.ibm.icu.text.NumberFormat.Field.DECIMAL_SEPARATOR to NumberFormat.Field.DECIMAL_SEPARATOR)
        for((af,bf) in fields) {
            val ap=if(af==null) FieldPosition(17) else FieldPosition(af)
            val bp=if(bf==null) FormatFieldPosition(17) else FormatFieldPosition(bf)
            ap.beginIndex=41;ap.endIndex=43;bp.setBeginIndex(41);bp.setEndIndex(43)
            val ao=StringBuilder("prefix:");val bo=StringBuilder("prefix:")
            assertEquals(outcome { a.formatMeasures(ao,ap,*source) },outcome { b.formatMeasures(bo,bp,*measures) },"list/$label/$af")
            assertEquals(ao.toString(),bo.toString());assertEquals(ap.beginIndex,bp.getBeginIndex());assertEquals(ap.endIndex,bp.getEndIndex())
            ap.beginIndex=41;ap.endIndex=43;bp.setBeginIndex(41);bp.setEndIndex(43)
            val rawA=StringBuffer("prefix:");val rawB=StringBuilder("prefix:")
            assertEquals(outcome { a.format(source,rawA,ap) },outcome { b.format(measures,rawB,bp) },"array/$label/$af")
            assertEquals(rawA.toString(),rawB.toString());assertEquals(ap.beginIndex,bp.getBeginIndex());assertEquals(ap.endIndex,bp.getEndIndex())
            if(values.size==1) {
                ap.beginIndex=41;ap.endIndex=43;bp.setBeginIndex(41);bp.setEndIndex(43)
                val singleA=StringBuffer("prefix:");val singleB=StringBuilder("prefix:")
                assertEquals(outcome { a.format(source[0],singleA,ap) },outcome { b.format(measures[0],singleB,bp) },"single/$label/$af")
                assertEquals(ap.beginIndex,bp.getBeginIndex());assertEquals(ap.endIndex,bp.getEndIndex())
            }
        }
    }
    @Test fun localizedWidthsSingleListsIntegerTruncationAndCurrenciesMatch() {
        val inputs=listOf(emptyList(),listOf(Measure(23,MeasureUnit.CELSIUS)),listOf(Measure(70,MeasureUnit.FOOT),Measure(5.3,MeasureUnit.INCH)),listOf(Measure(-3.9,MeasureUnit.METER),Measure(0.125,MeasureUnit.CENTIMETER)),listOf(Measure(12345.678,MeasureUnit.METER)),listOf(Measure(2,Currency.getInstance("USD"))),listOf(Measure(2.9,Currency.getInstance("USD")),Measure(1.25,Currency.getInstance("EUR"))),listOf(Measure(1,MeasureUnit.YEAR),Measure(2,MeasureUnit.MONTH),Measure(3.125,MeasureUnit.DAY)),listOf(Measure(Double.NaN,MeasureUnit.METER)),listOf(Measure(Double.POSITIVE_INFINITY,MeasureUnit.SECOND)))
        for(id in listOf("en_US","fr","de","ru","pl","ar","fa","ja","zh","he","hi","fi","bn","th","root")) for(width in MeasureFormat.FormatWidth.entries) {
            val a=Source.getInstance(SourceLocale(id),Source.FormatWidth.valueOf(width.name));val b=MeasureFormat.getInstance(ULocale(id),width)
            assertEquals(a.width.name,b.getWidth()!!.name);assertEquals(a.locale.toString(),b.getLocale().toString())
            for((i,values) in inputs.withIndex()) compare(a,b,values,"$id/$width/$i")
            for(unit in listOf(MeasureUnit.METER,MeasureUnit.FOOT,MeasureUnit.HOUR,MeasureUnit.CELSIUS)) assertEquals(a.getUnitDisplayName(sourceUnit(unit)),b.getUnitDisplayName(unit))
        }
    }
    @Test fun numericDurationOrderMissingFieldsRoundingOverflowAndLocaleDigitsMatch() {
        val h=MeasureUnit.HOUR;val m=MeasureUnit.MINUTE;val s=MeasureUnit.SECOND
        val inputs=listOf(listOf(Measure(5,h),Measure(37,m),Measure(23.4,s)),listOf(Measure(5,h),Measure(7,s)),listOf(Measure(3,m),Measure(4.56,s)),listOf(Measure(27.9,h),Measure(123.4,m)),listOf(Measure(0,h),Measure(0,s)),listOf(Measure(-1,h),Measure(2,m)),listOf(Measure(2,m),Measure(1,h)),listOf(Measure(1,h),Measure(2,h)),listOf(Measure(Double.NaN,h),Measure(2,m)),listOf(Measure(Double.POSITIVE_INFINITY,h),Measure(2.5,s)),listOf(Measure(1.5,m),Measure(Double.NaN,s)),listOf(Measure(1,h),Measure(4,MeasureUnit.METER)))
        for(id in listOf("en_US","fr","da","fi","ar","fa","bn","my","zh","en@numbers=thai")) {
            val a=Source.getInstance(SourceLocale(id),Source.FormatWidth.NUMERIC);val b=MeasureFormat.getInstance(ULocale(id),MeasureFormat.FormatWidth.NUMERIC)
            for((i,input) in inputs.withIndex()) compare(a,b,input,"$id/$i")
        }
    }
    @Test fun customNumberFormatPerUnitsClonesAndCurrencySpecializationMatch() {
        for(id in listOf("en_US","fr","ar")) for(width in MeasureFormat.FormatWidth.entries) {
            val an=com.ibm.icu.text.DecimalFormat("#,##0.000;(#,##0.000)",com.ibm.icu.text.DecimalFormatSymbols(SourceLocale(id)))
            val bn=DecimalFormat("#,##0.000;(#,##0.000)",DecimalFormatSymbols(ULocale(id)))
            val a=Source.getInstance(SourceLocale(id),Source.FormatWidth.valueOf(width.name),an);val b=MeasureFormat.getInstance(ULocale(id),width,bn)
            an.maximumFractionDigits=0;bn.setMaximumFractionDigits(0)
            compare(a,b,listOf(Measure(-1234.5678,MeasureUnit.METER)),"$id/$width/custom")
            compare(a,b,listOf(Measure(1.6,MeasureUnit.HOUR),Measure(3.5,MeasureUnit.MINUTE)),"$id/$width/numeric")
            compare(a,b,listOf(Measure(12.345,Currency.getInstance("USD"))),"$id/$width/currency")
            for((unit,per) in listOf(MeasureUnit.METER to MeasureUnit.SECOND,MeasureUnit.LITER to MeasureUnit.KILOMETER,MeasureUnit.POUND to MeasureUnit.SQUARE_INCH)) {
                val ao=StringBuilder("prefix:");val bo=StringBuilder("prefix:");val ap=FieldPosition(1);val bp=FormatFieldPosition(1)
                assertEquals(outcome { a.formatMeasurePerUnit(SourceMeasure(3.125,sourceUnit(unit)),sourceUnit(per),ao,ap) },outcome { b.formatMeasurePerUnit(Measure(3.125,unit),per,bo,bp) })
                assertEquals(ap.beginIndex,bp.getBeginIndex());assertEquals(ap.endIndex,bp.getEndIndex())
            }
            val ac=a.clone() as Source;val bc=b.clone() as MeasureFormat
            assertEquals(a,ac);assertEquals(b,bc);assertEquals(b.hashCode(),bc.hashCode())
            val n=b.getNumberFormat();n.setMaximumFractionDigits(0);assertEquals(a.numberFormat.maximumFractionDigits,b.getNumberFormat().getMaximumFractionDigits())
        }
        val a=Source.getCurrencyFormat(java.util.Locale.US);val b=MeasureFormat.getCurrencyFormat(java.util.Locale.US)
        for(code in listOf("USD","EUR","JPY")) {
            val ca=com.ibm.icu.util.CurrencyAmount(12.5,com.ibm.icu.util.Currency.getInstance(code));val cb=CurrencyAmount(12.5,Currency.getInstance(code))
            assertEquals(a.format(ca),b.format(cb))
            val ap=java.text.ParsePosition(0);val bp=ParsePosition(0)
            val pa=a.parseObject(a.format(ca),ap) as com.ibm.icu.util.CurrencyAmount?;val pb=b.parseObject(b.format(cb),bp) as CurrencyAmount?
            assertEquals(pa?.number?.toString(),pb?.getNumber()?.toString());assertEquals(pa?.currency?.currencyCode,pb?.getCurrency()?.getCurrencyCode());assertEquals(ap.index,bp.index)
        }
        assertEquals(outcome { a.format(null) },outcome { b.format(null) })
        assertEquals(outcome { a.format(123) },outcome { b.format(123) })
        assertEquals(Source.getInstance(SourceLocale.US,Source.FormatWidth.WIDE)==a,MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.WIDE)==b)
    }
    @Test fun invalidInputsRetainFieldPositionMutationAndRangePatternsMatch() {
        val a=Source.getInstance(SourceLocale.US,Source.FormatWidth.WIDE);val b=MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.WIDE)
        for(input in listOf(null,123,"bad",listOf(1,"bad"),arrayOf("bad"))) {
            val ap=FieldPosition(1).also { it.beginIndex=9;it.endIndex=10 };val bp=FormatFieldPosition(1).also { it.setBeginIndex(9);it.setEndIndex(10) }
            assertEquals(outcome { a.format(input,StringBuffer("prefix"),ap) },outcome { b.format(input,StringBuilder("prefix"),bp) })
            assertEquals(ap.beginIndex,bp.getBeginIndex());assertEquals(ap.endIndex,bp.getEndIndex())
        }
        for(id in listOf("en_US","fr","ar","fa","ja","en@numbers=arab")) for(width in MeasureFormat.FormatWidth.entries) assertEquals(outcome { Source.getRangeFormat(SourceLocale(id),Source.FormatWidth.valueOf(width.name)) },outcome { MeasureFormat.getRangeFormat(ULocale(id),width) })
        for(width in listOf(null,MeasureFormat.FormatWidth.WIDE,MeasureFormat.FormatWidth.NUMERIC)) assertEquals(outcome { Source.getInstance(SourceLocale.US,width?.let { Source.FormatWidth.valueOf(it.name) }).width.name },outcome { MeasureFormat.getInstance(ULocale.US,width).getWidth()!!.name })
        assertEquals(outcome { a.parseObject("3 feet",java.text.ParsePosition(0)) },outcome { b.parseObject("3 feet",ParsePosition(0)) })
        val ignore=DontCareFieldPosition.INSTANCE;ignore.setBeginIndex(99);ignore.setEndIndex(101)
        assertEquals(-913028704,ignore.getField());assertEquals(0,ignore.getBeginIndex());assertEquals(0,ignore.getEndIndex())
    }
}
