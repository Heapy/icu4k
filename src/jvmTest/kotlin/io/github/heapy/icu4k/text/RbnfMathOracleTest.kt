package io.github.heapy.icu4k.text

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RbnfMathOracleTest {
    @Test fun roundingMatchesJavaForRawBinaryValues() {
        val random = Random(783)
        repeat(100_000) {
            val bits = random.nextLong()
            val value = Double.fromBits(bits)
            assertEquals(Math.round(value), RbnfMath.roundToLong(value), "bits=$bits")
        }
        for (half in listOf(-2.5, -1.5, -0.5, 0.5, 1.5, 2.5)) {
            for (value in listOf(Math.nextDown(half), half, Math.nextUp(half))) {
                assertEquals(Math.round(value), RbnfMath.roundToLong(value))
            }
        }
    }
}
