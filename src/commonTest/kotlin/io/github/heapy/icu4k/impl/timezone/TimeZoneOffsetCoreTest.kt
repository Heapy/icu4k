package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.Output
import kotlin.test.*

class TimeZoneOffsetCoreTest {
    @Test fun formattingPrecisionAndUtcIndicator() {
        assertEquals("+053045",TimeZoneOffsetCore.formatOffsetISO8601Basic(19_845_987,false,false,false))
        assertEquals("-05:30:45",TimeZoneOffsetCore.formatOffsetISO8601Extended(-19_845_987,false,false,false))
        assertEquals("+05",TimeZoneOffsetCore.formatOffsetISO8601Basic(18_000_000,false,true,false))
        assertEquals("+00:00",TimeZoneOffsetCore.formatOffsetISO8601Extended(-999,false,false,false))
        assertEquals("Z",TimeZoneOffsetCore.formatOffsetISO8601Basic(-59_999,true,false,true))
        assertEquals("+00:00:59",TimeZoneOffsetCore.formatOffsetISO8601Extended(59_999,true,false,false))
        assertEquals("Z",TimeZoneOffsetCore.formatOffsetISO8601Basic(Int.MIN_VALUE,true,false,false))
        assertFailsWith<IllegalArgumentException> { TimeZoneOffsetCore.formatOffsetISO8601Basic(86_400_000,false,false,false) }
    }
    @Test fun partialParsingAndStaleErrorState() {
        val cases = listOf(
            Triple("z trailing",0,1), Triple("+053045x",19_845_000,7),
            Triple("-05:30:45x",-19_845_000,9), Triple("+1:23",3_600_000,2),
            Triple("+2460",9_960_000,4), Triple("+23:60",82_800_000,3),
            Triple("+24:00",7_200_000,2), Triple("+١٢",0,0), Triple("bad",0,0),
        )
        for ((text,offset,end) in cases) {
            val pos = ParsePosition(0).also { it.errorIndex = 71 }
            val digits = Output(true)
            assertEquals(offset,TimeZoneOffsetCore.parseOffsetISO8601(text,pos,false,digits),text)
            assertEquals(end,pos.index,text)
            assertEquals(if (end == 0) 0 else 71,pos.errorIndex,text)
            assertEquals(end > 0 && !text.startsWith("z"),digits.value,text)
        }
        val pos = ParsePosition(1)
        assertEquals(18_000_000,TimeZoneOffsetCore.parseOffsetISO8601("!+0530",pos,true))
        assertEquals(4,pos.index)
        assertFailsWith<IndexOutOfBoundsException> { TimeZoneOffsetCore.parseOffsetISO8601("Z",ParsePosition(-1)) }
        val digits = Output(true)
        assertFailsWith<NullPointerException> { TimeZoneOffsetCore.parseOffsetISO8601(null,null,false,digits) }
        assertEquals(false,digits.value)
    }
    @Test fun roundTripsAtEverySecond() {
        for (seconds in -86_399..86_399) {
            val offset = seconds * 1000
            val text = TimeZoneOffsetCore.formatOffsetISO8601Extended(offset,true,false,false)
            val pos = ParsePosition(0)
            assertEquals(offset,TimeZoneOffsetCore.parseOffsetISO8601(text,pos),text)
            assertEquals(text.length,pos.index,text)
        }
    }
}
