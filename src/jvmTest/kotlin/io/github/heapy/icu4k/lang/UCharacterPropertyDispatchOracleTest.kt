package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.text.UTF16
import kotlin.random.Random
import kotlin.test.*

class UCharacterPropertyDispatchOracleTest {
    @Test fun allBinaryPropertiesMatchEveryUnicodeCodePoint() {
        for (property in UProperty.BINARY_START until UProperty.BINARY_LIMIT) for (c in 0..0x10ffff) {
            val expected = JavaCharacter.hasBinaryProperty(c, property)
            val actual = UCharacter.hasBinaryProperty(c, property)
            if (expected != actual) fail("binary $property c=$c expected=$expected actual=$actual")
        }
    }
    @Test fun genericDispatchAndInvalidInputsMatchOriginal() {
        val random = Random(418)
        val properties = (0 until UProperty.BINARY_LIMIT).toList() + (UProperty.INT_START until UProperty.INT_LIMIT).toList() +
            listOf(UProperty.GENERAL_CATEGORY_MASK, -1, UProperty.BINARY_LIMIT, UProperty.INT_LIMIT, UProperty.AGE, Int.MIN_VALUE, Int.MAX_VALUE)
        for (property in properties) {
            repeat(2000) {
                val c = random.nextInt(0x110000)
                assertEquals(JavaCharacter.getIntPropertyValue(c, property), UCharacter.getIntPropertyValue(c, property), "$property $c")
            }
            for (c in listOf(-1, Int.MIN_VALUE, 0x110000, Int.MAX_VALUE)) {
                val j = runCatching { JavaCharacter.getIntPropertyValue(c, property) }
                val k = runCatching { UCharacter.getIntPropertyValue(c, property) }
                assertEquals(j.isSuccess, k.isSuccess, "$property invalid $c")
                if (j.isSuccess) assertEquals(j.getOrThrow(), k.getOrThrow(), "$property $c")
                else assertTrue(k.exceptionOrNull() is IllegalArgumentException)
            }
        }
    }
    @Test fun stringsAndConveniencePredicatesMatchOriginal() {
        val random = Random(128)
        val pieces = listOf("", "A", "1", "\u0301", "😀", "🇧🇾", "1\ufe0f\u20e3", "👩‍💻", "\u2764\ufe0f", CharArray(1) { 0xd800.toChar() }.concatToString())
        val strings = pieces + List(250) { buildString { repeat(random.nextInt(6)) { append(pieces.random(random)) } } }
        for (s in strings) for (p in -1..UProperty.BINARY_LIMIT) {
            assertEquals(JavaCharacter.hasBinaryProperty(s, p), UCharacter.hasBinaryProperty(s, p), "$p $s")
        }
        repeat(20000) {
            val c = random.nextInt(0x110000)
            assertEquals(JavaCharacter.isUAlphabetic(c), UCharacter.isUAlphabetic(c))
            assertEquals(JavaCharacter.isULowercase(c), UCharacter.isULowercase(c))
            assertEquals(JavaCharacter.isUUppercase(c), UCharacter.isUUppercase(c))
            assertEquals(JavaCharacter.isUWhiteSpace(c), UCharacter.isUWhiteSpace(c))
            val s = UTF16.valueOf(c)
            assertEquals(JavaCharacter.hasBinaryProperty(c, UProperty.ALPHABETIC), UCharacter.hasBinaryProperty(s, UProperty.ALPHABETIC))
        }
    }
}
