// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.test.*

class BigIntegerTest {
    @Test fun radixSignAndLowBits() {
        assertEquals("18446744073709551617", BigInteger("10000000000000001", 16).toString())
        assertEquals(1L, BigInteger("18446744073709551617").toLong())
        assertEquals(-1, BigInteger("-18446744073709551617").toInt())
        assertEquals("-ff", BigInteger("-255").toString(16))
        assertEquals("255", BigInteger("255").toString(1))
        assertEquals(BigInteger("123"), BigInteger("+٠٠١٢٣"))
        assertEquals(0, BigInteger("-000").signum())
        assertTrue(BigInteger("-256") < BigInteger("-255"))
        assertEquals(BigInteger("16777217").toFloat().toDouble(), 16777216.0)
        assertFailsWith<NumberFormatException> { BigInteger("1.0") }
        assertFailsWith<NumberFormatException> { BigInteger("1-2") }
    }
}
