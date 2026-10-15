package io.github.heapy.icu4k.math

import com.ibm.icu.math.BigDecimal as JDecimal
import kotlin.random.Random
import kotlin.test.*

class IcuDecimalFormatOracleTest {
    private fun outcome(label: String, j: () -> Any?, k: () -> Any?) {
        val expected = runCatching(j); val actual = runCatching(k)
        assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass, label)
        assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message, label)
        if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow(), label)
    }
    @Test fun formatWidthsNotationRoundingAndDiagnostics() {
        val values = listOf("0.000", "-0", "1.2345", "-9.999", "99999", "1E+12", "1E-12", "123.45E-8", "-999.95", "0.00000001")
        val random = Random(410337)
        for (input in values) {
            val j = JDecimal(input); val k = IcuDecimalValue.parse(input); val mant = k.mant
            for (before in listOf(-2, -1, 0, 1, 3, 10)) for (after in listOf(-2, -1, 0, 2, 7)) outcome("$input/$before/$after", { j.format(before, after) }, { k.format(before, after) })
            repeat(1800) {
                val before = listOf(-2, -1, 0, 1, 3, 7)[random.nextInt(6)]
                val after = random.nextInt(-2, 8); val places = random.nextInt(-2, 5); val digits = random.nextInt(-2, 10)
                val form = random.nextInt(-2, 4); val round = random.nextInt(-2, 10)
                outcome("$input/$before/$after/$places/$digits/$form/$round", { j.format(before, after, places, digits, form, round) }, { k.format(before, after, places, digits, form, round) })
            }
            // Valid combinations ensure rounding/padding paths are exercised beyond argument validation.
            for (before in listOf(-1, 3, 9)) for (after in listOf(-1, 0, 2, 5)) for (places in listOf(-1, 1, 3)) for (digits in listOf(-1, 0, 3, 9)) for (form in 1..2) for (round in 0..7) {
                outcome("$input/$before/$after/$places/$digits/$form/$round", { j.format(before, after, places, digits, form, round) }, { k.format(before, after, places, digits, form, round) })
            }
            assertEquals(j.toString(), k.toString()); assertSame(mant, k.mant)
        }
    }
    @Test fun exactConversionsAndPointMotion() {
        val values = listOf("0.000", "127", "128", "-128", "-129", "32767", "32768", "-32768", "-32769", "2147483647", "-2147483648", "9223372036854775807", "9223372036854775807.0", "-9223372036854775808", "-9223372036854775808.0", "9223372036854775808.0", "999999999999999999", "55555555555555555555.0", "1.01", "-0.1", "1E+20", "1E-20")
        for (input in values) {
            val j = JDecimal(input); val k = IcuDecimalValue.parse(input)
            outcome("long/$input", { j.longValueExact() }, { k.longValueExact() })
            outcome("byte/$input", { j.byteValueExact() }, { k.byteValueExact() })
            outcome("short/$input", { j.shortValueExact() }, { k.shortValueExact() })
            assertEquals(j.signum(), k.signum()); assertEquals(j.toDouble().toRawBits(), k.doubleValue().toRawBits())
            assertEquals(j.toFloat().toRawBits(), k.floatValue().toRawBits())
            for (n in -20..20) {
                outcome("left/$input/$n", { j.movePointLeft(n).toString() }, { k.movePointLeft(n).toString() })
                outcome("right/$input/$n", { j.movePointRight(n).toString() }, { k.movePointRight(n).toString() })
            }
        }
    }
    @Test fun representationAndFloatDoubleRounding() {
        val values = listOf("0", "0.0", "-0", "1", "1.0", "1E0", "1.00", "1E2", "100", "-1.0", "1E-10")
        for (left in values) for (right in values) {
            val j = JDecimal(left); val k = IcuDecimalValue.parse(left)
            assertEquals(j == JDecimal(right), k.sameRepresentation(IcuDecimalValue.parse(right)), "$left/$right")
            assertEquals(j.hashCode(), k.representationHashCode())
        }
        val hard = listOf("1.0000000596046447753906250000000000000001", "1.0000000596046447753906249999999999999999", "-1.0000000596046447753906250000000000000001", "1E999999999", "-1E-999999999", "3.40282356779733661637539395458142568448E38")
        for (text in hard) {
            val j = JDecimal(text); val k = IcuDecimalValue.parse(text)
            val layout = k.toString()
            assertEquals(j.toFloat().toRawBits(), icuDecimalFloatFromCandidate(layout, layout.toDouble().toFloat()).toRawBits(), text)
        }
        val midpoint = IcuDecimalValue.parse("5").pow(IcuDecimalValue.parse("150")).also { it.exp -= 150 }
        for (value in listOf(midpoint, midpoint.add(IcuDecimalValue.parse("1E-200")), midpoint.subtract(IcuDecimalValue.parse("1E-200")))) {
            val text = value.toString()
            assertEquals(JDecimal(text).toFloat().toRawBits(), icuDecimalFloatFromCandidate(text, text.toDouble().toFloat()).toRawBits(), text)
        }
    }
}
