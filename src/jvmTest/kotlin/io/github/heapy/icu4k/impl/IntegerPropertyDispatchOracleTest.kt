package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.random.Random
import kotlin.test.*

class IntegerPropertyDispatchOracleTest {
    @Test fun everyIntegerPropertyAndMaximumMatchesOriginal() {
        val p = UCharacterProperty.INSTANCE
        val random = Random(781)
        for (which in UProperty.INT_START until UProperty.INT_LIMIT) {
            assertEquals(JavaCharacter.getIntPropertyMaxValue(which), UCharacter.getIntPropertyMaxValue(which), "max $which")
            repeat(20000) {
                val c = random.nextInt(0x110000)
                assertEquals(JavaCharacter.getIntPropertyValue(c, which), p.getEnumeratedIntPropertyValue(c, which), "$which $c")
            }
            for (c in listOf(Int.MIN_VALUE, -1, 0, 0xd800, 0xdbff, 0xdc00, 0xdfff, 0x10ffff, 0x110000, Int.MAX_VALUE)) {
                val j = runCatching { JavaCharacter.getIntPropertyValue(c, which) }
                val k = runCatching { p.getEnumeratedIntPropertyValue(c, which) }
                assertEquals(j.isSuccess, k.isSuccess, "$which invalid $c")
                if (j.isSuccess) assertEquals(j.getOrThrow(), k.getOrThrow(), "$which $c")
                else assertTrue(k.exceptionOrNull() is IllegalArgumentException)
            }
        }
        for (which in -10..0x8000) {
            assertEquals(JavaCharacter.getIntPropertyMaxValue(which), UCharacter.getIntPropertyMaxValue(which), "max $which")
            assertEquals(JavaCharacter.getIntPropertyMinValue(which), UCharacter.getIntPropertyMinValue(which), "min $which")
        }
        for (which in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(-1, UCharacter.getIntPropertyMaxValue(which))
            assertEquals(0, UCharacter.getIntPropertyMinValue(which))
        }
    }
}
