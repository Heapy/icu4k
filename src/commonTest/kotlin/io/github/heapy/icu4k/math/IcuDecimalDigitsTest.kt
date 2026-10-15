package io.github.heapy.icu4k.math

import kotlin.test.*

class IcuDecimalDigitsTest {
    private fun digits(text: String): ByteArray = ByteArray(text.length) { (text[it] - '0').toByte() }
    @Test fun carryBorrowAndVirtualLengths() {
        assertContentEquals(digits("1000"), IcuDecimalDigits.addSub(digits("999"), 3, digits("1"), 1, 1, false))
        assertContentEquals(digits("0001"), IcuDecimalDigits.addSub(digits("1000"), 4, digits("999"), 3, -1, false))
        assertContentEquals(digits("2127"), IcuDecimalDigits.addSub(digits("12"), 4, digits("103"), 3, 9, false))
        assertContentEquals(digits("111"), IcuDecimalDigits.addSub(digits("120"), 3, digits("1"), 1, -9, false))
    }
    @Test fun arrayReuseAndZeroRanges() {
        val a = digits("123")
        assertSame(a, IcuDecimalDigits.addSub(a, 3, digits("1"), 1, 1, true))
        assertContentEquals(digits("124"), a)
        assertSame(a, IcuDecimalDigits.extend(a, 3))
        assertContentEquals(digits("12400"), IcuDecimalDigits.extend(a, 5))
        val b = digits("999")
        assertNotSame(b, IcuDecimalDigits.addSub(b, 3, digits("1"), 1, 1, true))
        assertContentEquals(digits("000"), b)
        assertFalse(IcuDecimalDigits.allZero(digits("100"), -9))
        assertTrue(IcuDecimalDigits.allZero(digits("100"), 1))
        assertTrue(IcuDecimalDigits.allZero(digits("123"), Int.MAX_VALUE))
    }
}
