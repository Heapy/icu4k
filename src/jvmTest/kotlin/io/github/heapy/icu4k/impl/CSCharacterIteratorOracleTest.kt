package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CSCharacterIterator as JavaIterator
import kotlin.random.Random
import kotlin.test.*

class CSCharacterIteratorOracleTest {
    @Test fun cursorOperationsMatchOriginal() {
        val random = Random(821)
        for (text in listOf("", "A😀B", CharArray(2) { if (it == 0) 0xd800.toChar() else 0xffff.toChar() }.concatToString())) {
            val j = JavaIterator(text); val k = CSCharacterIterator(text)
            repeat(1000) {
                val operation = random.nextInt(6)
                val pos = random.nextInt(text.length + 1)
                val expected = when (operation) { 0 -> j.first(); 1 -> j.last(); 2 -> j.next(); 3 -> j.previous(); 4 -> j.setIndex(pos); else -> j.current() }
                val actual = when (operation) { 0 -> k.first(); 1 -> k.last(); 2 -> k.next(); 3 -> k.previous(); 4 -> k.setIndex(pos); else -> k.current() }
                assertEquals(expected, actual)
                assertEquals(j.index, k.getIndex())
                assertEquals(j.endIndex, k.getEndIndex())
                assertEquals(j.clone().current(), k.clone().current())
            }
        }
    }
}
