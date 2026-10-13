package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCharacterUtility as JavaUtility
import kotlin.random.Random
import kotlin.test.*

class UCharacterUtilityOracleTest {
    private fun call(name: String, vararg args: Any): Any? {
        val types = args.map { when (it) { is Int -> Int::class.javaPrimitiveType!!; is Byte -> Byte::class.javaPrimitiveType!!; is Char -> Char::class.javaPrimitiveType!!; else -> it.javaClass } }.toTypedArray()
        return JavaUtility::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(null, *args)
    }
    @Test fun conversionsAndNameByteOperationsMatchOriginal() {
        val random = Random(341)
        for (c in 0..0x10ffff) if (JavaUtility.isNonCharacter(c) != UCharacterUtility.isNonCharacter(c)) fail("noncharacter $c")
        for (c in listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE)) assertEquals(JavaUtility.isNonCharacter(c), UCharacterUtility.isNonCharacter(c))
        repeat(1000) {
            val m = random.nextInt(0x10000).toChar(); val l = random.nextInt(0x10000).toChar()
            assertEquals(call("toInt", m, l), UCharacterUtility.toInt(m, l))
            val fields = List(6) { ByteArray(random.nextInt(10)) { random.nextInt(1, 256).toByte() } }
            val bytes = fields.fold(byteArrayOf()) { a, b -> a + b + byteArrayOf(0) }
            var index = 0
            for (field in fields) {
                val j = StringBuffer("!"); val k = StringBuilder("!")
                assertEquals(call("getNullTermByteSubString", j, bytes, index), UCharacterUtility.getNullTermByteSubString(k, bytes, index))
                assertEquals(j.toString(), k.toString())
                val text = "!" + field.map { (it.toInt() and 255).toChar() }.joinToString("") + "?"
                assertEquals(call("compareNullTermByteSubString", text, bytes, 1, index), UCharacterUtility.compareNullTermByteSubString(text, bytes, 1, index))
                assertEquals(call("compareNullTermByteSubString", "", bytes, 0, index), UCharacterUtility.compareNullTermByteSubString("", bytes, 0, index))
                index += field.size + 1
            }
            for (count in -1..6) assertEquals(call("skipNullTermByteSubString", bytes, 0, count), UCharacterUtility.skipNullTermByteSubString(bytes, 0, count))
            val start = random.nextInt(bytes.size)
            val length = random.nextInt(bytes.size - start + 1)
            val end = random.nextInt(256).toByte()
            assertEquals(call("skipByteSubString", bytes, start, length, end), UCharacterUtility.skipByteSubString(bytes, start, length, end))
        }
    }
}
