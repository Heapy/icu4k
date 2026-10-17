// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiTest {
    @Test fun mixedParagraphMapsAndWriter() {
        val bidi = Bidi()
        bidi.setPara("abc אבג", 0, null)
        assertEquals("abc גבא", bidi.writeReordered(0))
        assertContentEquals(intArrayOf(0, 1, 2, 3, 6, 5, 4), bidi.getVisualMap())
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 1, 1, 1), bidi.getLevels())
        for (i in 0 until bidi.getLength()) {
            assertEquals(i, bidi.getLogicalIndex(bidi.getVisualIndex(i)))
        }
        assertEquals(2, bidi.countRuns())
    }

    @Test fun lineTracksParentValidity() {
        val bidi = Bidi()
        bidi.setPara("abc אבג", 0, null)
        val line = bidi.setLine(4, 7)
        assertEquals("גבא", line.writeReordered(0))
        assertFailsWith<IndexOutOfBoundsException> { bidi.setPara("abc", 0, byteArrayOf(0)) }
        assertFailsWith<IllegalStateException> { line.getLength() }
        bidi.setPara("abc אבג", 0, null)
        assertEquals(3, line.getLength())
    }

    @Test fun constructorAndCallerTextAlias() {
        val text = "abc אבג".toCharArray()
        val bidi = Bidi(text, 0, null, 0, text.size, Bidi.DIRECTION_LEFT_TO_RIGHT)
        assertSame(text, bidi.getText())
        text[0] = 'X'
        assertEquals("Xbc גבא", bidi.writeReordered(0))
        assertEquals(Bidi.RTL, Bidi.getBaseDirection("אבג"))
        assertEquals("גבא", Bidi.writeReverse("אבג", 0))
    }

    @Test fun reusableRunsOnly() {
        val bidi = Bidi()
        bidi.setReorderingMode(Bidi.REORDER_RUNS_ONLY.toInt())
        repeat(3) {
            bidi.setPara("abc אבג", 0, null)
            assertEquals("אבג abc", bidi.writeReordered(0))
            assertEquals(Bidi.REORDER_RUNS_ONLY.toInt(), bidi.getReorderingMode())
        }
    }
}
