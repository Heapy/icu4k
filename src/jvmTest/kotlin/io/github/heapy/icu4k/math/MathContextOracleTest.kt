package io.github.heapy.icu4k.math

import java.io.*
import kotlin.test.*
import com.ibm.icu.math.MathContext as JContext

class MathContextOracleTest {
    @Test fun constructorsBoundsModesAndDiagnostics() {
        for (digits in listOf(Int.MIN_VALUE, -1, 0, 1, 9, 34, 999999999, 1000000000, Int.MAX_VALUE)) {
            val shortJ = runCatching { JContext(digits) }; val shortK = runCatching { MathContext(digits) }
            assertEquals(shortJ.exceptionOrNull()?.message, shortK.exceptionOrNull()?.message)
            assertEquals(shortJ.getOrNull()?.toString(), shortK.getOrNull()?.toString())
            for (form in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, Int.MAX_VALUE)) for (lost in listOf(false, true)) {
                val midJ = runCatching { JContext(digits, form, lost) }; val midK = runCatching { MathContext(digits, form, lost) }
                assertEquals(midJ.exceptionOrNull()?.message, midK.exceptionOrNull()?.message)
                assertEquals(midJ.getOrNull()?.toString(), midK.getOrNull()?.toString())
                val twoJ = runCatching { JContext(digits, form) }; val twoK = runCatching { MathContext(digits, form) }
                assertEquals(twoJ.exceptionOrNull()?.message, twoK.exceptionOrNull()?.message)
                assertEquals(twoJ.getOrNull()?.toString(), twoK.getOrNull()?.toString())
                for (round in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, Int.MAX_VALUE)) {
                    val j = runCatching { JContext(digits, form, lost, round) }; val k = runCatching { MathContext(digits, form, lost, round) }
                    assertEquals(j.exceptionOrNull()?.javaClass, k.exceptionOrNull()?.javaClass)
                    assertEquals(j.exceptionOrNull()?.message, k.exceptionOrNull()?.message)
                    assertEquals(j.getOrNull()?.toString(), k.getOrNull()?.toString())
                    if (j.isSuccess) {
                        assertEquals(j.getOrThrow().digits, k.getOrThrow().getDigits()); assertEquals(j.getOrThrow().form, k.getOrThrow().getForm())
                        assertEquals(j.getOrThrow().lostDigits, k.getOrThrow().getLostDigits()); assertEquals(j.getOrThrow().roundingMode, k.getOrThrow().getRoundingMode())
                    }
                }
            }
        }
        assertEquals(JContext.DEFAULT.toString(), MathContext.DEFAULT.toString())
        for (field in JContext::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }) {
            assertEquals(field.getInt(null), MathContext::class.java.getField(field.name).getInt(null), field.name)
        }
    }
    private fun roundTrip(value: Any): Any {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(value) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }
    @Test fun serializationShapeAndUnvalidatedStreamState() {
        val jDescriptor = ObjectStreamClass.lookup(JContext::class.java)
        val kDescriptor = ObjectStreamClass.lookup(MathContext::class.java)
        assertEquals(jDescriptor.serialVersionUID, kDescriptor.serialVersionUID)
        assertEquals(jDescriptor.fields.map { it.name to it.type }, kDescriptor.fields.map { it.name to it.type })
        for (digits in listOf(0, 9, 999999999)) for (form in 0..2) for (round in 0..7) {
            val j = JContext(digits, form, true, round); val k = MathContext(digits, form, true, round)
            assertEquals(roundTrip(j).toString(), roundTrip(k).toString())
        }
        val j = JContext(9); val k = MathContext(9)
        for ((field, value) in listOf("digits" to -1, "form" to 42, "roundingMode" to -99)) {
            JContext::class.java.getDeclaredField(field).also { it.isAccessible = true }.setInt(j, value)
            MathContext::class.java.getDeclaredField(field).also { it.isAccessible = true }.setInt(k, value)
        }
        assertEquals(j.toString(), k.toString())
        assertEquals(roundTrip(j).toString(), roundTrip(k).toString())
    }
}
