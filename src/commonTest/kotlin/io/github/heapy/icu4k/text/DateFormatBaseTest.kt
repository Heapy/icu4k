@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateFormatBaseTest {
    private class Probe:DateFormat {
        constructor():super() { calendar=GregorianCalendar(SimpleTimeZone(0,"test"),ULocale.US);numberFormat=NumberFormat.getInstance(ULocale.US) }
        private constructor(other:Probe):super(other,Unit)
        override fun cloneNative():DateFormat=Probe(this)
        override fun format(calendar:Calendar?,output:StringBuilder?,position:FormatFieldPosition?):StringBuilder=output!!.append(calendar!!.getTimeInMillis())
        override fun parse(text:String?,calendar:Calendar?,position:ParsePosition?) {
            if(text=="bad") { position!!.errorIndex=position.index;return }
            calendar!!.set(2024,1,if(text=="invalid") 31 else 29)
            position!!.index+=text!!.length
        }
    }
    @Test fun dateAndNumericDispatchAndStrictParsing() {
        val format=Probe()
        assertEquals("123",format.format(Date(123)));assertEquals("456",format.format(456L))
        assertNotNull(format.parse("ok"));assertNull(format.parse("bad",ParsePosition(0)))
        format.setLenient(false)
        val position=ParsePosition(2);assertNull(format.parse("invalid",position));assertEquals(2,position.index);assertEquals(2,position.errorIndex)
        assertFailsWith<ParseException> { format.parse("invalid") }
    }
    @Test fun cloneAndSharedBooleanAttributesMatchSource() {
        val format=Probe();val copy=format.clone()
        assertNotSame(format.getCalendar(),copy.getCalendar());assertNotSame(format.getNumberFormat(),copy.getNumberFormat())
        copy.setBooleanAttribute(DateFormat.BooleanAttribute.PARSE_PARTIAL_MATCH,false)
        assertFalse(format.getBooleanAttribute(DateFormat.BooleanAttribute.PARSE_PARTIAL_LITERAL_MATCH))
        assertFalse(format.getBooleanAttribute(null));assertFailsWith<NullPointerException> { format.setBooleanAttribute(null,true) }
        copy.setLenient(false);assertTrue(format.isCalendarLenient());assertFalse(format.isLenient())
    }
    @Test fun dateFieldsAndNumberFormatConfiguration() {
        assertSame(DateFormat.Field.YEAR,DateFormat.Field.ofCalendarField(Calendar.YEAR))
        assertNull(DateFormat.Field.ofCalendarField(Calendar.ORDINAL_MONTH));assertFailsWith<IllegalArgumentException>{DateFormat.Field.ofCalendarField(-1)}
        val format=Probe();val numbers=DecimalFormat("#,##0.00")
        format.setNumberFormat(numbers)
        assertTrue(numbers.isGroupingUsed());assertFalse(format.getNumberFormat()!!.isGroupingUsed())
        assertTrue(format.getNumberFormat()!!.isParseIntegerOnly());assertEquals(0,format.getNumberFormat()!!.getMinimumFractionDigits())
    }
}
