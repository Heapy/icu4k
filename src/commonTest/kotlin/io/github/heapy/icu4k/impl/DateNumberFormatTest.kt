package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.FormatFieldPosition
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateNumberFormatTest {
    @Test fun localizedPaddingAndFieldPositions() {
        val f = DateNumberFormat(ULocale("en_US_DNFCOMMONFORMAT"), "٠١٢٣٤٥٦٧٨٩", "latn")
        f.setMaximumIntegerDigits(5); f.setMinimumIntegerDigits(4)
        val pos = FormatFieldPosition(0)
        assertEquals("prefix-٠٠١٢", f.format(-12L, StringBuilder("prefix"), pos).toString())
        assertEquals(0, pos.getBeginIndex()); assertEquals(4, pos.getEndIndex())
        assertEquals(5, f.getMaximumIntegerDigits()); assertEquals(4, f.getMinimumIntegerDigits())
    }
    @Test fun parsingThresholdAndPositiveOnly() {
        val f = DateNumberFormat(ULocale("en_US_DNFCOMMONPARSE"), '0', "latn")
        val pos = ParsePosition(0).also { it.errorIndex = 7 }
        assertEquals(922337203685477580L, f.parse("9223372036854775807", pos))
        assertEquals(18, pos.index); assertEquals(7, pos.errorIndex)
        f.setParsePositiveOnly(true); pos.index = 0
        assertNull(f.parse("-12", pos)); assertEquals(0, pos.index)
        f.setParsePositiveOnly(false)
        assertEquals(-12L, f.parse("-١٢", pos)); assertEquals(3, pos.index)
    }
    @Test fun localeCacheRetainsFirstDigitArrayAndCopiesIt() {
        val locale = ULocale("en_US_DNFCOMMONCACHE")
        val first = DateNumberFormat(locale, "abcdefghij", "latn")
        val second = DateNumberFormat(locale, '0', "arab")
        assertContentEquals("abcdefghij".toCharArray(), second.getDigits())
        first.setZeroDigit('0')
        val copy = second.getDigits(); copy[0] = '!'
        assertEquals('a', second.getZeroDigit())
        assertContentEquals("abcdefghij".toCharArray(), DateNumberFormat(locale, "short", null).getDigits())
    }
    @Test fun cloneAndLongToIntTruncation() {
        val f = DateNumberFormat(ULocale("en_US_DNFCOMMONCLONE"), '0', "latn")
        assertFailsWith<IndexOutOfBoundsException> { f.format(1L) }
        f.setMaximumIntegerDigits(20)
        assertEquals("0", f.format(1L shl 32)); assertEquals("-0", f.format(Long.MIN_VALUE))
        val c = f.clone()
        assertEquals(f, c); assertNotSame(f, c)
        c.setZeroDigit('٠')
        assertEquals("123", f.format(123L)); assertEquals("١٢٣", c.format(123L))
    }
    @Test fun nonLongFormatsRetainSourceUnsupportedContracts() {
        val f = DateNumberFormat(ULocale("en_US_DNFCOMMONUNSUPPORTED"), '0', "latn")
        assertFailsWith<UnsupportedOperationException> { f.format(1.0, null as StringBuilder?, null) }
        assertFailsWith<UnsupportedOperationException> { f.format(BigInteger("123"), null as StringBuilder?, null) }
        assertFailsWith<UnsupportedOperationException> { f.format(BigDecimal("1.25"), null as StringBuilder?, null) }
        assertFailsWith<UnsupportedOperationException> { f.format(io.github.heapy.icu4k.math.BigDecimal("1.25"), null as StringBuilder?, null) }
    }
}
