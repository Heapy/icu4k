@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateIntervalFormatTest {
    private val start = 1705323723456L
    private fun formatter(skeleton: String, id: String = "en_US") = DateIntervalFormat.getInstance(skeleton, ULocale(id), DateIntervalInfo(ULocale(id))).also { it.setTimeZone(TimeZone.GMT_ZONE) }
    @Test fun compactLocaleCalendarAndTimeFallbackSourceGoldens() {
        assertEquals("Jan 15\u2009–\u200916, 2024", formatter("yMMMd").format(DateInterval(start,start+86400000)))
        assertEquals("13:02:03\u2009–\u200913:03:03",formatter("Hms","fr").format(DateInterval(start,start+60000)))
        assertEquals("2024/1/15 13時02分～14時02分",formatter("yMdHm","ja").format(DateInterval(start,start+3600000)))
        assertEquals("2023年腊月5至2024年正月6",formatter("yMMMd","zh@calendar=chinese").format(DateInterval(start,start+2678400000L)))
    }
    @Test fun formattedFieldsEndpointConstraintsAndAttributedRuns() {
        val value = formatter("yMMMd").formatToValue(DateInterval(start,start+86400000))
        val span = DateIntervalFormat.SpanField.DATE_INTERVAL_SPAN
        val rows = mutableListOf<List<Any?>>()
        val position = ConstrainedFieldPosition()
        while(value.nextPosition(position)) rows.add(listOf(position.getField(),position.getFieldValue(),position.getStart(),position.getLimit()))
        assertEquals<List<List<Any?>>>(listOf(listOf(DateFormat.Field.MONTH,null,0,3),listOf(span,0,4,6),listOf(DateFormat.Field.DAY_OF_MONTH,null,4,6),listOf(span,1,9,11),listOf(DateFormat.Field.DAY_OF_MONTH,null,9,11),listOf(DateFormat.Field.YEAR,null,13,17)),rows)
        position.reset();position.constrainFieldAndValue(span,1)
        assertTrue(value.nextPosition(position));assertEquals(9,position.getStart());assertEquals(11,position.getLimit());assertFalse(value.nextPosition(position))
        val iterator=value.toCharacterIterator();iterator.setIndex(4)
        assertEquals(0,iterator.getAttribute(span));assertSame(DateFormat.Field.DAY_OF_MONTH,iterator.getAttribute(DateFormat.Field.DAY_OF_MONTH))
        iterator.setIndex(7);assertTrue(iterator.getAttributes().isEmpty())
        val output=StringBuilder("prefix:");assertSame(output,value.appendTo(output));assertEquals("prefix:$value",output.toString())
        assertEquals(value.toString().toList(),value.toList());assertEquals(value.toString().substring(4,6),value.subSequence(4,6).toString())
        assertFailsWith<IndexOutOfBoundsException> { value[-1] };assertFailsWith<IndexOutOfBoundsException> { value[value.length] }
        for((start,end) in listOf(-1 to 0,0 to value.length+1,2 to 1)) assertFailsWith<IndexOutOfBoundsException> { value.subSequence(start,end) }
    }
    @Test fun equalDatesClonesFreezeBoundariesAndUnsupportedInputs() {
        val f=formatter("yMMMd");val same=DateInterval(start,start)
        assertEquals("Jan 15, 2024",f.format(same))
        val position=ConstrainedFieldPosition().also { it.constrainField(DateIntervalFormat.SpanField.DATE_INTERVAL_SPAN) }
        assertFalse(f.formatToValue(same).nextPosition(position))
        val copy=f.clone();assertNotSame(f,copy)
        copy.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));assertEquals(TimeZone.GMT_ZONE.getID(),f.getTimeZone().getID())
        val info=f.getDateIntervalInfo();assertTrue(info.isFrozen());assertSame(info,f.getDateIntervalInfo())
        assertFailsWith<UnsupportedOperationException> { info.setFallbackIntervalPattern("{1}/{0}") }
        assertFailsWith<UnsupportedOperationException> { (f.getRawPatterns() as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { f.parseObject("Jan",ParsePosition(0)) }
        assertFailsWith<IllegalArgumentException> { f.format(123) }
        assertFailsWith<NullPointerException> { f.format(null as Any?) }
        val c=Calendar.getInstance(TimeZone.GMT_ZONE,ULocale.US);c.setTimeInMillis(start)
        val d=c.clone();d.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"))
        assertFailsWith<IllegalArgumentException> { f.format(c,d,StringBuilder(),FormatFieldPosition(0)) }
    }
    @Test fun reversedCustomPatternsAndNativeSubtypeCloneHook() {
        val info=DateIntervalInfo().also { it.setIntervalPattern("yMd",Calendar.DATE,"latestFirst:y/M/d <> d") }
        val f=DateIntervalFormat.getInstance("yMd",ULocale.US,info).also { it.setTimeZone(TimeZone.GMT_ZONE) }
        val value=f.formatToValue(DateInterval(start,start+86400000))
        assertEquals("2024/1/16 <> 15",value.toString())
        val p=ConstrainedFieldPosition().also { it.constrainField(DateIntervalFormat.SpanField.DATE_INTERVAL_SPAN) }
        assertTrue(value.nextPosition(p));assertEquals(1,p.getFieldValue());assertEquals(7,p.getStart());assertEquals(9,p.getLimit())
        class Derived : DateIntervalFormat {
            val marker: String
            constructor() : super("yMMMd",DateIntervalInfo(ULocale.US),SimpleDateFormat("MMM d, y",ULocale.US)) { marker="subtype";setTimeZone(TimeZone.GMT_ZONE) }
            private constructor(other: Derived) : super(other,Unit) { marker=other.marker }
            override fun cloneNative(): DateIntervalFormat = Derived(this)
        }
        val subtype=Derived();val copy=subtype.clone()
        assertTrue(copy is Derived);assertEquals("subtype",copy.marker)
        assertEquals(subtype.format(DateInterval(start,start+86400000)),copy.format(DateInterval(start,start+86400000)))
    }
}
