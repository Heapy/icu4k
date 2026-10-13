package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CharacterIteratorWrapper as JavaWrapper
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class CharacterIteratorAdapterOracleTest {
    @Test fun absoluteRangesNoncharactersAndExceptionsMatch() {
        val random = Random(443)
        fun outcome(action: () -> Int): String = try { action().toString() } catch (_: IndexOutOfBoundsException) { "bounds" } catch (_: IllegalArgumentException) { "argument" }
        repeat(100) {
            val text = CharArray(30) { when (random.nextInt(3)) { 0 -> 0xffff.toChar(); 1 -> random.nextInt(0xd800, 0xe000).toChar(); else -> random.nextInt(256).toChar() } }.concatToString()
            val begin = random.nextInt(15)
            val j = JavaWrapper(java.text.StringCharacterIterator(text, begin, 30, begin))
            val k = CharacterIteratorWrapper(StringCharacterIterator(text, begin, 30, begin))
            repeat(300) {
                when (random.nextInt(9)) {
                    0 -> assertEquals(j.next(), k.next())
                    1 -> assertEquals(j.previous(), k.previous())
                    2 -> assertEquals(j.nextCodePoint(), k.nextCodePoint())
                    3 -> assertEquals(j.previousCodePoint(), k.previousCodePoint())
                    4 -> assertEquals(j.currentCodePoint(), k.currentCodePoint())
                    5 -> {
                        val delta = random.nextInt(-35, 36)
                        assertEquals(outcome { j.moveIndex(delta) }, outcome { k.moveIndex(delta) })
                    }
                    6 -> {
                        val index = random.nextInt(-2, 33)
                        assertEquals(outcome { j.setIndex(index); 0 }, outcome { k.setIndex(index); 0 })
                    }
                    7 -> { j.setToLimit(); k.setToLimit() }
                    else -> {
                        val a = CharArray(35) { '!' }; val b = a.copyOf()
                        assertEquals(j.getText(a, 2), k.getText(b, 2))
                        assertContentEquals(a, b)
                        assertEquals(j.characterIterator.index, k.getCharacterIterator().getIndex())
                    }
                }
                assertEquals(j.index, k.getIndex()); assertEquals(j.current(), k.current()); assertEquals(j.length, k.getLength())
            }
        }
    }
}
