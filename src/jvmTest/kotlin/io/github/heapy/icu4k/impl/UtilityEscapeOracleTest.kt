package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Utility as JavaUtility
import kotlin.random.Random
import kotlin.test.*

class UtilityEscapeOracleTest {
    @Test fun escapeDecodingMatchesOriginal() {
        val random = Random(726)
        val parts = listOf("u0041", "U0001F600", "x{10FFFF}", "x{0000D800}", "uD800", "uDC00", "x{00000041}", "x{110000}", "UFFFFFFFF", "777", "400", "08", "x", "c😀", "c", "n", "q", "\\uDC00", "\\uD800", CharArray(2) { if (it == 0) 0xd800.toChar() else 0xdc00.toChar() }.concatToString())
        repeat(4000) {
            val text = buildString { repeat(random.nextInt(1, 8)) { if (random.nextBoolean()) append('\\'); append(parts.random(random)) } }
            for (offset in -1..text.length) assertEquals(JavaUtility.unescapeAndLengthAt(text, offset), Utility.unescapeAndLengthAt(text, offset), "$text $offset")
            assertEquals(JavaUtility.unescapeLeniently(text), Utility.unescapeLeniently(text))
            val expected = runCatching { JavaUtility.unescape(text) }
            val actual = runCatching { Utility.unescape(text) }
            if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow())
            else { assertTrue(actual.exceptionOrNull() is IllegalArgumentException); assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message) }
        }
    }
}
