package io.github.heapy.icu4k.impl

import kotlin.random.Random
import kotlin.test.*

class ICUNumberParsingOracleTest {
    @Test fun literalBitsAndRejectionMatchJavaParser() {
        val random = Random(429)
        val fixtures = listOf("NaN", "+NaN", "-NaN", "Infinity", "+Infinity", "-Infinity", "1f", ".5D", " 1.25\u0000", "0x.8p1", "0X1.P0", "0x1.fffffffffffff8p1023", "0x0.fffffffffffff8p-1022", "0x1p-1075", "0x1.0000000000000000000000001p-1075", "0x1p99999999999999999999999", "0x1p-999999999999999999999", "", "nan", "NaNf", "InfinityD", "0x1", "1_0", "1e", "1.2x", "\u00a01")
        val texts = fixtures + List(5000) {
            val count = random.nextInt(1, 100)
            val digits = buildString { repeat(count) { append("0123456789abcdef"[random.nextInt(16)]) } }
            val split = random.nextInt(count + 1)
            (if (random.nextBoolean()) "-" else "+") + "0x" + digits.substring(0, split) + "." + digits.substring(split) + "p" + random.nextInt(-1500, 1500) + (if (random.nextBoolean()) "F" else "")
        } + List(2000) { java.lang.Double.toHexString(Double.fromBits(random.nextLong())) } + List(2000) { Double.fromBits(random.nextLong()).toString() }
        for (text in texts) {
            val j = runCatching { java.lang.Double.parseDouble(text) }
            val k = runCatching { ICUNumberParsing.parseDouble(text) }
            assertEquals(j.isSuccess, k.isSuccess, text)
            if (j.isSuccess) assertEquals(j.getOrThrow().toBits(), k.getOrThrow().toBits(), text)
            else assertTrue(k.exceptionOrNull() is NumberFormatException)
        }
    }
}
