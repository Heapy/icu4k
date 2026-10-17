package io.github.heapy.icu4k.util
import kotlin.test.*
class DateIntervalTest {
    @Test fun endpointsEqualityOverflowHashAndText() {
        val interval = DateInterval(10, -20)
        assertEquals(10L, interval.getFromDate()); assertEquals(-20L, interval.getToDate())
        assertEquals("10 -20", interval.toString()); assertEquals(-10, interval.hashCode())
        assertEquals(interval, DateInterval(10, -20)); assertNotEquals(interval, DateInterval(-20, 10))
        assertEquals(0, DateInterval(Long.MAX_VALUE, 1).hashCode())
        assertEquals("-9223372036854775808 9223372036854775807", DateInterval(Long.MIN_VALUE, Long.MAX_VALUE).toString())
    }
}
