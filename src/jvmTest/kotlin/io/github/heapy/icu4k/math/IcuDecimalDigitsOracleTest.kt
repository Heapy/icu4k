package io.github.heapy.icu4k.math

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.random.Random
import kotlin.test.*

class IcuDecimalDigitsOracleTest {
    private val source = com.ibm.icu.math.BigDecimal::class.java
    private val add = source.getDeclaredMethod("byteaddsub", ByteArray::class.java, Int::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).also { it.isAccessible = true }
    private val extend = source.getDeclaredMethod("extend", ByteArray::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
    private val zero = source.getDeclaredMethod("allzero", ByteArray::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
    private fun invoke(method: Method, vararg arguments: Any?): Any? = try { method.invoke(null, *arguments) } catch (failure: InvocationTargetException) { throw failure.cause!! }
    @Test fun decimalArrayOperationsAndAliasing() {
        val random = Random(83427)
        repeat(14000) {
            val ka = ByteArray(random.nextInt(1, 36)) { random.nextInt(10).toByte() }
            val kb = if (it % 5 == 0) ka else ByteArray(random.nextInt(1, 36)) { random.nextInt(10).toByte() }
            val ja = ka.copyOf(); val jb = if (kb === ka) ja else kb.copyOf()
            val avlen = random.nextInt(1, 42); val bvlen = random.nextInt(1, 42)
            val multiplier = random.nextInt(-9, 10); val reuse = random.nextBoolean()
            val expected = invoke(add, ja, avlen, jb, bvlen, multiplier, reuse) as ByteArray
            val actual = IcuDecimalDigits.addSub(ka, avlen, kb, bvlen, multiplier, reuse)
            val label = "$avlen/$bvlen/$multiplier/$reuse"
            assertContentEquals(expected, actual, label)
            assertContentEquals(ja, ka, "A mutations $label"); assertContentEquals(jb, kb, "B mutations $label")
            assertEquals(expected === ja, actual === ka, "reuse A $label"); assertEquals(expected === jb, actual === kb, "reuse B $label")
        }
    }
    @Test fun helpersAndUncheckedBoundaryFailures() {
        for (input in listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(1, 0, 0), byteArrayOf(0, 9, 0))) {
            for (start in listOf(Int.MIN_VALUE, -1, 0, 1, input.size, input.size + 1, Int.MAX_VALUE)) assertEquals(invoke(zero, input, start), IcuDecimalDigits.allZero(input, start))
            for (length in listOf(input.size, input.size + 1, input.size + 9)) {
                val expected = invoke(extend, input, length) as ByteArray
                val actual = IcuDecimalDigits.extend(input, length)
                assertContentEquals(expected, actual); assertEquals(expected === input, actual === input)
            }
        }
        for (input in listOf(null, byteArrayOf(1, 2))) for (length in listOf(-1, 0, 1, 2)) {
            val expected = runCatching { invoke(extend, input, length) }
            val actual = runCatching { IcuDecimalDigits.extend(input, length) }
            // Negative array sizes and array bounds use portable Kotlin exception classes on other hosts.
            assertEquals(expected.isSuccess, actual.isSuccess)
            if (expected.isSuccess) assertContentEquals(expected.getOrThrow() as ByteArray, actual.getOrThrow())
        }
    }
}
