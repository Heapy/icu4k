@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.impl.DontCareFieldPosition
import kotlin.test.*

class MeasureFormatTest {
    @Test fun localeListsAndNumericDurationsMatchSourceGoldens() {
        val values=arrayOf(Measure(70,MeasureUnit.FOOT),Measure(5.3,MeasureUnit.INCH))
        val duration=arrayOf(Measure(5,MeasureUnit.HOUR),Measure(7.25,MeasureUnit.SECOND))
        val cases=listOf(
            Triple("en_US",MeasureFormat.FormatWidth.WIDE,"70 feet, 5.3 inches"),
            Triple("fr",MeasureFormat.FormatWidth.WIDE,"70 pieds et 5,3\u00a0pouces"),
            Triple("fr",MeasureFormat.FormatWidth.SHORT,"70\u202fpi et 5,3\u202fpo"),
            Triple("ja",MeasureFormat.FormatWidth.NARROW,"70′5.3″"),
            Triple("ar",MeasureFormat.FormatWidth.NARROW,"70 قدمًا و5.3 بوصة"))
        for((id,width,expected) in cases) assertEquals(expected,MeasureFormat.getInstance(ULocale(id),width).formatMeasures(*values))
        val arrayFormatter=MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.WIDE)
        assertEquals("70 feet, 5.3 inches",arrayFormatter.format(values))
        assertEquals("70 feet, 5.3 inches",arrayFormatter.format(values.toList()))
        assertFailsWith<IllegalArgumentException> { arrayFormatter.format(arrayOf("bad")) }
        assertEquals("5:00:07.25",MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.NUMERIC).formatMeasures(*duration))
        assertEquals("5.00.07,25",MeasureFormat.getInstance(ULocale("fi"),MeasureFormat.FormatWidth.NUMERIC).formatMeasures(*duration))
        val integer=MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.WIDE)
        assertEquals("1 foot, 2.5 inches",integer.formatMeasures(Measure(1.9,MeasureUnit.FOOT),Measure(2.5,MeasureUnit.INCH)))
    }
    @Test fun fieldPositionsDetachedGettersAndShallowCloneValueSemantics() {
        val formatter=MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.WIDE)
        val position=FormatFieldPosition(NumberFormat.Field.FRACTION)
        val output=StringBuilder("prefix:")
        assertSame(output,formatter.formatMeasures(output,position,Measure(70,MeasureUnit.FOOT),Measure(5.3,MeasureUnit.INCH)))
        assertEquals("prefix:70 feet, 5.3 inches",output.toString())
        // Source reports the empty fraction after the first integer measure.
        assertEquals(9,position.getBeginIndex());assertEquals(9,position.getEndIndex())
        val custom=DecimalFormat("0.000",DecimalFormatSymbols(ULocale.US))
        val f=MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.WIDE,custom)
        custom.setMaximumFractionDigits(0)
        assertEquals("2.500 meters",f.format(Measure(2.5,MeasureUnit.METER)))
        val nf=f.getNumberFormat();nf.setMaximumFractionDigits(0)
        assertEquals(3,f.getNumberFormat().getMaximumFractionDigits())
        val copy=f.clone() as MeasureFormat
        assertNotSame(f,copy);assertEquals(f,copy);assertEquals(f.hashCode(),copy.hashCode())
        val ignore=DontCareFieldPosition.INSTANCE;ignore.setBeginIndex(42);ignore.setEndIndex(43)
        assertEquals(0,ignore.getBeginIndex());assertEquals(0,ignore.getEndIndex())
    }
    @Test fun currencySpecializationPerUnitFormattingAndUnsupportedParsing() {
        val f=MeasureFormat.getCurrencyFormat(ULocale.US)
        val amount=CurrencyAmount(12.5,Currency.getInstance("USD"))
        assertEquals("$12.50",f.format(amount))
        val position=ParsePosition(0)
        val parsed=f.parseObject("$12.50",position) as CurrencyAmount
        assertEquals("USD",parsed.getCurrency().getCurrencyCode());assertEquals(12.5,parsed.getNumber().toDouble());assertEquals(6,position.index)
        assertFailsWith<IllegalArgumentException> { f.format(Measure(1,MeasureUnit.METER)) }
        val basic=MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.WIDE)
        assertFailsWith<UnsupportedOperationException> { basic.parseObject("1 meter",ParsePosition(0)) }
        val text=basic.formatMeasurePerUnit(Measure(3.5,MeasureUnit.METER),MeasureUnit.SECOND,StringBuilder(),FormatFieldPosition(0))
        assertEquals("3.5 meters per second",text.toString())
    }
}
