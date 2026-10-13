package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetComparisonOracleTest {
    @Test fun comparisonStylesAndHelpersMatchIcu() {
        val random = Random(184)
        repeat(2000) {
            fun pair(): Pair<JavaSet, UnicodeSet> {
                val j = JavaSet(); val k = UnicodeSet()
                repeat(random.nextInt(10)) { val c = random.nextInt(0x110000); val end = minOf(c + random.nextInt(20), 0x10ffff); j.add(c, end); k.add(c, end) }
                for (s in listOf("", "ab", "xy", "😀x")) if (random.nextBoolean()) { j.add(s); k.add(s) }
                return j to k
            }
            val (ja, ka) = pair(); val (jb, kb) = pair()
            assertEquals(ja.compareTo(jb), ka.compareTo(kb))
            for (style in UnicodeSet.ComparisonStyle.entries) assertEquals(ja.compareTo(jb, JavaSet.ComparisonStyle.valueOf(style.name)), ka.compareTo(kb, style))
            val items = listOf("", "a", "😀", "😀x")
            assertEquals(ja.compareTo(items), ka.compareTo(items))
            for (s in items) for (cp in listOf(0, 65, 0xd800, 0xffff, 0x1f600, 0x10ffff)) {
                assertEquals(JavaSet.compare(s, cp), UnicodeSet.compare(s, cp))
                assertEquals(JavaSet.compare(cp, s), UnicodeSet.compare(cp, s))
            }
        }
    }
}
