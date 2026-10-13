package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetRangesOracleTest {
    @Test fun rangesSearchStrippingAndBridgesMatchIcu() {
        val random = Random(811)
        repeat(400) {
            val j = JavaSet(); val k = UnicodeSet()
            repeat(20) { val a = random.nextInt(0x110000); val b = minOf(a + random.nextInt(10), 0x10ffff); j.add(a, b); k.add(a, b) }
            j.add(65, 67).add(0x1f600).add("ab"); k.add(65, 67).add(0x1f600).add("ab")
            val ji = j.ranges().iterator(); val ki = k.ranges().iterator()
            while (ji.hasNext()) {
                assertTrue(ki.hasNext()); val a = ji.next(); val b = ki.next()
                assertEquals(a.codepoint, b.codepoint); assertEquals(a.codepointEnd, b.codepointEnd); assertEquals(a.toString(), b.toString())
            }
            assertFalse(ki.hasNext())
            val text = "xAB😀y" + CharArray(15) { random.nextInt(0x10000).toChar() }.concatToString()
            for (sense in listOf(false, true)) {
                assertEquals(j.stripFrom(text, sense), k.stripFrom(text, sense))
                for (index in 0..text.length) {
                    assertEquals(j.findIn(text, index, sense), k.findIn(text, index, sense))
                    assertEquals(j.findLastIn(text, index, sense), k.findLastIn(text, index, sense))
                }
            }
            assertEquals(j.addBridges(JavaSet(0, 0x10ffff)).toPattern(true), k.addBridges(UnicodeSet(0, 0x10ffff)).toPattern(true))
        }
    }
}
