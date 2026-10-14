@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import java.math.BigDecimal
import java.math.BigInteger
import java.lang.reflect.InvocationTargetException
import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity

class DecimalBcdStorageOracleTest {
    private val base = JQuantity::class.java.superclass
    private fun field(quantity: JQuantity, name: String): Int = base.getDeclaredField(name).also { it.isAccessible = true }.getInt(quantity)
    private fun call(quantity: JQuantity, name: String, vararg arguments: Any): Any? {
        val types = arguments.map { when (it) { is Int -> Int::class.javaPrimitiveType; is Byte -> Byte::class.javaPrimitiveType; is Long -> Long::class.javaPrimitiveType; else -> it.javaClass } }.toTypedArray()
        val method = JQuantity::class.java.getDeclaredMethod(name, *types).also { it.isAccessible = true }
        return try { method.invoke(quantity, *arguments) } catch (e: InvocationTargetException) { throw e.targetException }
    }
    private fun storage(text: String): DecimalBcdStorage {
        val decimal = UnitDecimal(text)
        return DecimalBcdStorage().also {
            if (decimal.sign != 0) {
                val value = decimal.magnitude.toLongOrNull()
                if (value == null) it.readBigIntegerToBcd(decimal.magnitude)
                else if (value <= Int.MAX_VALUE) it.readIntToBcd(value.toInt()) else it.readLongToBcd(value)
                it.scale -= decimal.scale; it.compact()
            }
        }
    }
    private fun check(j: JQuantity, k: DecimalBcdStorage, label: String) {
        assertEquals(field(j, "scale"), k.scale, label)
        assertEquals(field(j, "precision"), k.precision, label)
        assertEquals(j.isUsingBytes, k.usingBytes, label)
        assertEquals(j.checkHealth(), k.checkHealth(), label)
        assertEquals(call(j, "toNumberString"), k.toNumberString(), label)
        for (i in -1..maxOf(20, k.precision + 2)) assertEquals(call(j, "getDigitPos", i), k.getDigitPos(i), "$label/$i")
        assertEquals(j.toBigDecimal().toString(), k.toDecimal(j.isNegative).toString(), label)
    }
    @Test fun constructorsCompactionAndMutationSchedulesMatchSource() {
        val random = Random(927441)
        repeat(100) { schedule ->
            val text = buildString { append(random.nextInt(1, 10)); repeat(random.nextInt(0, 120)) { append(random.nextInt(10)) }; append('E'); append(random.nextInt(-100, 101)) }
            val j = JQuantity(BigDecimal(text)); val k = storage(text)
            check(j, k, text)
            repeat(35) { step ->
                if (k.precision == 0) return@repeat
                when (random.nextInt(4)) {
                    0 -> { val count = random.nextInt(10); call(j, "shiftLeft", count); k.shiftLeft(count) }
                    1 -> { val count = random.nextInt(k.precision); call(j, "shiftRight", count); k.shiftRight(count) }
                    2 -> { val count = random.nextInt(k.precision + 1); call(j, "popFromLeft", count); k.popFromLeft(count) }
                    3 -> { val pos = random.nextInt(k.precision); val digit = random.nextInt(10).toByte(); call(j, "setDigitPos", pos, digit); k.setDigitPos(pos, digit) }
                }
                call(j, "compact"); k.compact()
                check(j, k, "$schedule/$step")
                val jc = JQuantity(j); val kc = k.copy()
                check(jc, kc, "copy $schedule/$step")
            }
        }
    }
    @Test fun integerLoadBoundariesAndDiagnosticsMatchSource() {
        for (n in listOf("1", "10", "2147483647", "2147483648", "9999999999999999", "10000000000000000", "9223372036854775807", "9223372036854775808", "1" + "0".repeat(100))) {
            val j = JQuantity(BigInteger(n)); val k = storage(n)
            check(j, k, n)
        }
        for (position in listOf(0, 15, 16, 50)) for (digit in listOf<Byte>(0, 9, 10, -1)) {
            val j = JQuantity(123456789); val k = storage("123456789")
            call(j, "setDigitPos", position, digit); k.setDigitPos(position, digit)
            assertEquals(j.checkHealth(), k.checkHealth(), "$position/$digit")
        }
    }
    @Test fun decimalConversionPreservesStorageSpecificExponentAndOverflowBehavior() {
        fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { e.javaClass.simpleName }
        for (text in listOf("-12345", "123456789012345678901234")) for (scale in listOf(0, 17, -17, Int.MIN_VALUE, Int.MAX_VALUE)) for (exponent in listOf(0, 3, -3)) {
            val j = JQuantity(BigDecimal(text)); val k = storage(text)
            base.getDeclaredField("scale").also { it.isAccessible = true }.setInt(j, scale)
            base.getDeclaredField("exponent").also { it.isAccessible = true }.setInt(j, exponent)
            k.scale = scale; k.exponent = exponent
            assertEquals(outcome { j.toBigDecimal().toString() }, outcome { k.toDecimal(j.isNegative).toString() }, "$text/$scale/$exponent")
            val copy = k.copy()
            assertEquals(scale, copy.scale)
            assertEquals(exponent, copy.exponent)
            assertEquals(k.toNumberString(), copy.toNumberString())
        }
    }

}
