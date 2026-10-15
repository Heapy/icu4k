package io.github.heapy.icu4k.impl.number

import kotlin.random.Random
import kotlin.test.*

class CommonFloatToDecimalOracleTest {
    @Test fun shortestBinary32DecimalMatchesJdk() {
        val bits = linkedSetOf(0, Int.MIN_VALUE, 0x7f800000, 0xff800000.toInt(), 0x7fc00001, 0x7f7fffff)
        for (bit in 1..255) { bits.add(bit); bits.add(bit or Int.MIN_VALUE) }
        for (exponent in 1..254) for (delta in -1..1) { bits.add((exponent shl 23) + delta); bits.add(((exponent shl 23) + delta) or Int.MIN_VALUE) }
        val random = Random(471035)
        repeat(1000) { bits.add(random.nextInt()) }
        for (bit in bits) {
            val value = Float.fromBits(bit)
            assertEquals(java.lang.Float.toString(value), CommonDoubleToDecimal.format(value), bit.toUInt().toString(16))
        }
    }
}
