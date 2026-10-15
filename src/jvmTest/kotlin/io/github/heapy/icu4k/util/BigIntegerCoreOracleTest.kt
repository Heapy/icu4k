// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.BigIntegerCore
import java.math.BigInteger as JInteger
import kotlin.random.Random
import kotlin.test.*

class BigIntegerCoreOracleTest {
    @Test fun radixAndNumberConversionsMatchJdk() {
        val random = Random(7813)
        val values = listOf("0", "-0", "2147483648", "-9223372036854775808", "18446744073709551617", "16777217") + List(180) {
            (if (random.nextBoolean()) "-" else "+") + buildString { repeat(random.nextInt(1, 350)) { append(('0'.code + random.nextInt(10)).toChar()) } }
        }
        for (text in values) {
            val j = JInteger(text)
            val k = BigIntegerCore(text)
            assertEquals(j.toString(), k.toString(), text)
            assertEquals(j.signum(), k.sign)
            assertEquals(j.hashCode(), k.hashCode(), text)
            assertEquals(j.toLong(), k.toLong(), text)
            assertEquals(j.toInt(), k.toInt(), text)
            assertEquals(j.toFloat().toBits(), k.toFloat().toBits(), text)
            assertEquals(j.toDouble().toBits(), k.toDouble().toBits(), text)
            for (radix in -1..38) {
                val encoded = j.toString(radix)
                assertEquals(encoded, k.toString(radix), "$text/$radix")
                if (radix in 2..36) assertEquals(k, BigIntegerCore(encoded, radix))
            }
            val other = values[random.nextInt(values.size)]
            assertEquals(j.compareTo(JInteger(other)).coerceIn(-1, 1), k.compareTo(BigIntegerCore(other)))
        }
    }
    private fun outcome(block: () -> Any?): String = try { block().toString() } catch (e: NullPointerException) { "null" } catch (e: NumberFormatException) { "NFE:${e.message}" }
    @Test fun parsingDiagnosticsMatchJdk() {
        val values = listOf(null, "", "+", "-", "--1", "+-1", "1+2", " 1", "1 ", "1.0", "1e2", "٠١٢٣", "１２３", "ffff", "zzzzzzzzzzzzzzzzzzzzzzzzzzz", "00000000000000000000000000000000000000x")
        for (text in values) for (radix in listOf(-1, 0, 1, 2, 8, 10, 16, 36, 37)) {
            assertEquals(outcome { JInteger(text, radix) }, outcome { BigIntegerCore(text, radix) }, "$text/$radix")
        }
    }
}
