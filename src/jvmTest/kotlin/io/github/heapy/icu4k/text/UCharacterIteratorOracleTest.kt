package io.github.heapy.icu4k.text

import com.ibm.icu.text.UCharacterIterator as JavaIterator
import kotlin.random.Random
import kotlin.test.*

class UCharacterIteratorOracleTest {
    @Test fun randomizedCursorOperationsMatchIcu() {
        val random = Random(78_004)
        repeat(100) {
            val text = CharArray(random.nextInt(40)) {
                listOf(0x61, 0xd800, 0xdbff, 0xdc00, 0xdfff, 0xffff)[random.nextInt(6)].toChar()
            }
            for (arrayMode in listOf(false, true)) {
                val expected = if (arrayMode) JavaIterator.getInstance(text) else JavaIterator.getInstance(text.concatToString())
                val actual = if (arrayMode) UCharacterIterator.getInstance(text) else UCharacterIterator.getInstance(text.concatToString())
                repeat(200) {
                    when (random.nextInt(10)) {
                        0 -> assertEquals(expected.next(), actual.next())
                        1 -> assertEquals(expected.previous(), actual.previous())
                        2 -> assertEquals(expected.nextCodePoint(), actual.nextCodePoint())
                        3 -> assertEquals(expected.previousCodePoint(), actual.previousCodePoint())
                        4 -> assertEquals(expected.currentCodePoint(), actual.currentCodePoint())
                        5 -> {
                            val index = random.nextInt(text.size + 1)
                            expected.setIndex(index)
                            actual.setIndex(index)
                        }
                        6 -> {
                            val delta = random.nextInt(-50, 51)
                            assertEquals(expected.moveIndex(delta), actual.moveIndex(delta))
                        }
                        7 -> {
                            val delta = random.nextInt(-50, 51)
                            val result = runCatching { expected.moveCodePointIndex(delta) }
                            if (result.isSuccess) assertEquals(result.getOrThrow(), actual.moveCodePointIndex(delta))
                            else assertFailsWith<IndexOutOfBoundsException> { actual.moveCodePointIndex(delta) }
                        }
                        8 -> { expected.setToStart(); actual.setToStart() }
                        else -> { expected.setToLimit(); actual.setToLimit() }
                    }
                    assertEquals(expected.index, actual.getIndex())
                    assertEquals(expected.current(), actual.current())
                    assertEquals(expected.text, actual.getText())
                    assertEquals(expected.clone().index, actual.clone().getIndex())
                }
            }
        }
    }

    @Test fun sliceBoundariesAndCopyMatchIcu() {
        val text = charArrayOf(0xd800.toChar(), 0xdc00.toChar(), 0xd800.toChar(), 0xdc00.toChar())
        for (start in 0..text.size) for (limit in start..text.size) {
            val expected = JavaIterator.getInstance(text, start, limit)
            val actual = UCharacterIterator.getInstance(text, start, limit)
            for (index in 0..limit - start) {
                expected.setIndex(index)
                actual.setIndex(index)
                assertEquals(expected.currentCodePoint(), actual.currentCodePoint())
                assertEquals(expected.nextCodePoint(), actual.nextCodePoint())
                assertEquals(expected.index, actual.getIndex())
                assertEquals(expected.previousCodePoint(), actual.previousCodePoint())
                assertEquals(expected.index, actual.getIndex())
            }
            val a = CharArray(10) { '_' }
            val b = a.copyOf()
            assertEquals(expected.getText(a, 1), actual.getText(b, 1))
            assertContentEquals(a, b)
        }
    }
}
