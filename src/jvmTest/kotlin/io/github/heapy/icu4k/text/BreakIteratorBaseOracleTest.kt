package io.github.heapy.icu4k.text

import com.ibm.icu.text.BreakIterator as JavaBreak
import kotlin.test.*

class BreakIteratorBaseOracleTest {
    private class JavaFixture : JavaBreak() {
        private var position = 0
        private var text: java.text.CharacterIterator = java.text.StringCharacterIterator("abcde")
        override fun first(): Int { position = 0; return 0 }
        override fun last(): Int { position = 5; return 5 }
        override fun next(n: Int): Int { repeat(kotlin.math.abs(n)) { if (n > 0) next() else previous() }; return position }
        override fun next(): Int = following(position)
        override fun previous(): Int { val p = listOf(0, 2, 5).lastOrNull { it < position } ?: return DONE; position = p; return p }
        override fun following(offset: Int): Int { val p = listOf(0, 2, 5).firstOrNull { it > offset } ?: return DONE; position = p; return p }
        override fun current(): Int = position
        override fun getText(): java.text.CharacterIterator = text
        override fun setText(newText: java.text.CharacterIterator) { text = newText; first() }
    }
    private class CommonFixture : BreakIterator() {
        private var position = 0
        private var text: CharacterIterator = StringCharacterIterator("abcde")
        override fun clone(): BreakIterator = CommonFixture().also { it.position = position; it.text = text.clone() }
        override fun first(): Int { position = 0; return 0 }
        override fun last(): Int { position = 5; return 5 }
        override fun next(n: Int): Int { repeat(kotlin.math.abs(n)) { if (n > 0) next() else previous() }; return position }
        override fun next(): Int = following(position)
        override fun previous(): Int { val p = listOf(0, 2, 5).lastOrNull { it < position } ?: return DONE; position = p; return p }
        override fun following(offset: Int): Int { val p = listOf(0, 2, 5).firstOrNull { it > offset } ?: return DONE; position = p; return p }
        override fun current(): Int = position
        override fun getText(): CharacterIterator = text
        override fun setText(newText: CharacterIterator) { text = newText; first() }
    }
    @Test fun inheritedOperationsAndTextAdaptersMatchOriginal() {
        val j = JavaFixture(); val k = CommonFixture()
        for (offset in -2..7) {
            j.first(); k.first()
            assertEquals(j.preceding(offset), k.preceding(offset)); assertEquals(j.current(), k.current())
            j.last(); k.last()
            assertEquals(j.isBoundary(offset), k.isBoundary(offset)); assertEquals(j.current(), k.current())
        }
        assertEquals(j.ruleStatus, k.getRuleStatus())
        for (size in 0..3) {
            val ja = IntArray(size) { 99 }; val ka = ja.copyOf()
            assertEquals(j.getRuleStatusVec(ja), k.getRuleStatusVec(ka)); assertContentEquals(ja, ka)
        }
        assertEquals(j.getRuleStatusVec(null), k.getRuleStatusVec(null))
        j.setText("A😀"); k.setText("A😀")
        assertEquals(j.text.first(), k.getText().first())
        val builder = StringBuilder("abc")
        j.setText(builder as CharSequence); k.setText(builder as CharSequence)
        assertEquals(j.text.endIndex, k.getText().getEndIndex())
    }
}
