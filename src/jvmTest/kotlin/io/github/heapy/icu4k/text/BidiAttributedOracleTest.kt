// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import java.awt.font.NumericShaper
import java.awt.font.TextAttribute
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import kotlin.test.*

class BidiAttributedOracleTest {
    private class Trace(private val delegate: AttributedCharacterIterator, private val lateShape: Any?) : AttributedCharacterIterator by delegate {
        val calls = mutableListOf<String>()
        override fun getAttribute(attribute: AttributedCharacterIterator.Attribute?): Any? {
            calls += "attribute:$attribute@${delegate.index}"
            if (attribute === TextAttribute.NUMERIC_SHAPING && delegate.index == delegate.endIndex && lateShape != null) return lateShape
            return delegate.getAttribute(attribute)
        }
        override fun first(): Char { calls += "first"; return delegate.first() }
        override fun next(): Char { calls += "next"; return delegate.next() }
        override fun getBeginIndex(): Int { calls += "begin"; return delegate.beginIndex }
        override fun getEndIndex(): Int { calls += "end"; return delegate.endIndex }
    }
    private fun outcome(action: () -> Any?): String = try { "ok:${action()}" } catch (e: Throwable) {
        when (e) {
            is ClassCastException -> "cast"
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            is IllegalArgumentException -> "argument:${e.message}"
            is IllegalStateException -> "state:${e.message}"
            else -> throw e
        }
    }
    private fun iterator(text: String, direction: Any?, embedding: Any?, shape: Any?, begin: Int, end: Int, atEnd: Boolean, lateShape: Any?): Trace {
        val attributed = AttributedString(text)
        if (text.isNotEmpty()) {
            if (direction != null) attributed.addAttribute(TextAttribute.RUN_DIRECTION, direction)
            if (embedding != null) attributed.addAttribute(TextAttribute.BIDI_EMBEDDING, embedding)
            if (shape != null) attributed.addAttribute(TextAttribute.NUMERIC_SHAPING, shape)
        }
        return Trace(attributed.getIterator(null, begin, end).apply { if (atEnd) setIndex(end) }, lateShape)
    }
    @Test fun nativeAwtKeysIteratorCursorAndLateNumericShapingMatchSource() {
        assertSame(TextAttribute.RUN_DIRECTION, BidiAttributes.RUN_DIRECTION)
        assertSame(TextAttribute.BIDI_EMBEDDING, BidiAttributes.BIDI_EMBEDDING)
        assertSame(TextAttribute.NUMERIC_SHAPING, BidiAttributes.NUMERIC_SHAPING)
        val shaper = NumericShaper.getShaper(NumericShaper.ARABIC)
        for (text in listOf("", "123 אב a", "x123אבz", "a\uffff123", "\ud8001\udc00א")) {
            for (direction in listOf(null, false, true, "wrong")) for (embedding in listOf(null, 0, -1, 2, -128, 126, 300, 1L)) {
                for (late in listOf(false, true)) for (atEnd in listOf(false, true)) {
                    val begin = if (text.length > 4) 1 else 0; val end = if (text.length > 4) text.length - 1 else text.length
                    val s = iterator(text, direction, embedding, shaper, begin, end, atEnd, if (late) shaper else null)
                    val p = iterator(text, direction, embedding, shaper, begin, end, atEnd, if (late) shaper else null)
                    val sb = com.ibm.icu.text.Bidi("old אב", 0); val pb = Bidi("old אב", 0)
                    assertEquals(outcome { sb.setPara(s) }, outcome { pb.setPara(p) }, "setPara")
                    assertEquals(s.calls, p.calls, "cursor/attribute calls")
                    assertEquals(s.index, p.index)
                    assertEquals(outcome { sb.textAsString }, outcome { pb.getTextAsString() })
                    assertEquals(outcome { sb.levels.toList() }, outcome { pb.getLevels().toList() })
                    assertEquals(outcome { sb.writeReordered(2) }, outcome { pb.writeReordered(2) })
                }
            }
        }
        assertEquals(outcome { com.ibm.icu.text.Bidi(null as AttributedCharacterIterator?) }, outcome { Bidi(null as FormatAttributedCharacterIterator?) })
    }
    @Test fun wrongLateShaperLeavesPreviousParagraphValid() {
        val sb = com.ibm.icu.text.Bidi("אב old", 0); val pb = Bidi("אב old", 0)
        val s = iterator("123", null, null, null, 0, 3, false, "wrong")
        val p = iterator("123", null, null, null, 0, 3, false, "wrong")
        assertEquals("cast", outcome { sb.setPara(s) }); assertEquals("cast", outcome { pb.setPara(p) })
        assertEquals(sb.textAsString, pb.getTextAsString())
        assertEquals(sb.writeReordered(0), pb.writeReordered(0))
    }
}
