package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UCharacter as JavaCharacter
import com.ibm.icu.util.ValueIterator as JavaIterator
import io.github.heapy.icu4k.util.ValueIterator
import kotlin.random.Random
import kotlin.test.*

class NameIteratorOracleTest {
    private fun compare(j: JavaIterator, k: ValueIterator) {
        val je = JavaIterator.Element(); val ke = ValueIterator.Element()
        je.integer = -99; ke.integer = -99; je.value = "sentinel"; ke.value = "sentinel"
        while (true) {
            val more = j.next(je)
            assertEquals(more, k.next(ke))
            if (je.integer != ke.integer || je.value != ke.value) fail("${je.integer} ${je.value} vs ${ke.integer} ${ke.value}")
            if (!more) break
        }
        assertFalse(k.next(ke))
        assertEquals(je.integer, ke.integer)
        assertEquals(je.value, ke.value)
    }
    @Test fun fullIterationsAndChangingRangesMatchOriginal() {
        compare(JavaCharacter.getNameIterator(), UCharacter.getNameIterator())
        compare(JavaCharacter.getExtendedNameIterator(), UCharacter.getExtendedNameIterator())
        val random = Random(712)
        for (extended in listOf(false, true)) {
            val j = if (extended) JavaCharacter.getExtendedNameIterator() else JavaCharacter.getNameIterator()
            val k = if (extended) UCharacter.getExtendedNameIterator() else UCharacter.getNameIterator()
            for ((start, limit) in listOf(-20 to 20, 0x33f0 to 0x3420, 0x9ff0 to 0xa010, 0x10fff0 to 0x110020, -20 to -10, 0x120000 to 0x130000) + List(60) { val s = random.nextInt(0x110000); s to s + random.nextInt(1, 100) }) {
                j.setRange(start, limit); k.setRange(start, limit)
                compare(j, k)
                j.reset(); k.reset()
                compare(j, k)
            }
        }
        val j = JavaCharacter.getName1_0Iterator(); val k = UCharacter.getName1_0Iterator()
        j.setRange(1, 0); k.setRange(1, 0); compare(j, k)
    }
}
