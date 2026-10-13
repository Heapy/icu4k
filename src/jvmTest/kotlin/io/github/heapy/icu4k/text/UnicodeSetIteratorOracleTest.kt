package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.text.UnicodeSetIterator as JavaIterator
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetIteratorOracleTest {
    @Test fun iterationFieldsAndResetMatchOriginal() {
        val random = Random(971)
        repeat(400) {
            val jset = JavaSet(); val kset = UnicodeSet()
            repeat(15) { val start = random.nextInt(0x110000); val end = minOf(start + random.nextInt(20), 0x10ffff); jset.add(start, end); kset.add(start, end) }
            for (s in listOf("", "ab", "😀x")) if (random.nextBoolean()) { jset.add(s); kset.add(s) }
            val j = JavaIterator(jset); val k = UnicodeSetIterator(kset)
            repeat(4) { mode ->
                j.reset(); k.reset()
                if (mode >= 2) { j.skipToStrings(); k.skipToStrings() }
                while (true) {
                    val expected = if (mode % 2 == 0) j.next() else j.nextRange()
                    val actual = if (mode % 2 == 0) k.next() else k.nextRange()
                    assertEquals(expected, actual)
                    assertEquals(j.codepoint, k.codepoint)
                    assertEquals(j.codepointEnd, k.codepointEnd)
                    assertEquals(j.string, k.string)
                    assertEquals(j.getString(), k.getString())
                    if (!expected) break
                }
            }
        }
    }
}
