package io.github.heapy.icu4k.charset

import kotlin.test.*

class Utf8PrimitivesOracleTest {
    private val type = Class.forName("com.ibm.icu.charset.UTF8")
    private fun method(name: String, vararg types: Class<*>) = type.getDeclaredMethod(name, *types).apply { isAccessible = true }
    @Test fun allBytesLeadTrailPairsAndUnicodeLengthsMatchSource() {
        for ((name, port) in listOf<Pair<String, (Byte) -> Any>>("countTrailBytes" to { UTF8.countTrailBytes(it) }, "countBytes" to { UTF8.countBytes(it) }, "isSingle" to { UTF8.isSingle(it) }, "isLead" to { UTF8.isLead(it) }, "isTrail" to { UTF8.isTrail(it) })) {
            val source = method(name, Byte::class.javaPrimitiveType!!)
            for (byte in 0..255) assertEquals(source.invoke(null, byte.toByte()), port(byte.toByte()), "$name/$byte")
        }
        val three = method("isValidLead3AndT1", Int::class.javaPrimitiveType!!, Byte::class.javaPrimitiveType!!)
        val four = method("isValidLead4AndT1", Int::class.javaPrimitiveType!!, Byte::class.javaPrimitiveType!!)
        val trail = method("isValidTrail", Int::class.javaPrimitiveType!!, Byte::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
        for (byte in 0..255) {
            for (lead in 0xe0..0xef) assertEquals(three.invoke(null, lead, byte.toByte()), UTF8.isValidLead3AndT1(lead, byte.toByte()))
            for (lead in 0xf0..0xf4) assertEquals(four.invoke(null, lead, byte.toByte()), UTF8.isValidLead4AndT1(lead, byte.toByte()))
            for (length in 2..4) for (index in 1..<length) for (previous in 0..4) assertEquals(trail.invoke(null, previous, byte.toByte(), index, length), UTF8.isValidTrail(previous, byte.toByte(), index, length))
        }
        val length = method("length", Int::class.javaPrimitiveType!!)
        for (scalar in -1..0x110001) assertEquals(length.invoke(null, scalar), UTF8.length(scalar))
        for (scalar in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) assertEquals(length.invoke(null, scalar), UTF8.length(scalar))
    }
}
