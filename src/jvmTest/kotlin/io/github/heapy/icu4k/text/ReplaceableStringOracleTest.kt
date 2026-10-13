package io.github.heapy.icu4k.text

import com.ibm.icu.text.ReplaceableString as JavaReplaceableString
import kotlin.random.Random
import kotlin.test.*

class ReplaceableStringOracleTest {
    @Test fun randomizedEditingMatchesIcu() {
        val random = Random(78_003)
        val actual = ReplaceableString()
        val expected = JavaReplaceableString()
        repeat(2_000) {
            val length = actual.length()
            val start = random.nextInt(length + 1)
            val limit = random.nextInt(start, length + 1)
            when (random.nextInt(3)) {
                0 -> {
                    val units = CharArray(random.nextInt(8)) { random.nextInt(0x10000).toChar() }
                    val text = units.concatToString()
                    actual.replace(start, limit, text)
                    expected.replace(start, limit, text)
                }
                1 -> {
                    val units = CharArray(8) { random.nextInt(0x10000).toChar() }
                    actual.replace(start, limit, units, 2, 4)
                    expected.replace(start, limit, units, 2, 4)
                }
                else -> {
                    // Non-overlapping destination; keep growth bounded.
                    val dest = if (random.nextBoolean()) 0 else length
                    if (length < 80) {
                        actual.copy(start, limit, dest)
                        expected.copy(start, limit, dest)
                    }
                }
            }
            assertEquals(expected.toString(), actual.toString())
            assertEquals(expected.length(), actual.length())
            for (offset in 0 until actual.length()) {
                assertEquals(expected.charAt(offset), actual.charAt(offset))
                assertEquals(expected.char32At(offset), actual.char32At(offset))
                assertEquals(expected.char32At(offset), UTF16.charAt(actual, offset))
            }
        }
    }
}
