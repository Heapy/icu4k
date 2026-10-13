package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as J
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetCloseOverOracleTest {
    private fun compare(j: J, k: UnicodeSet, attribute: Int) {
        assertSame(j, j.closeOver(attribute)); assertSame(k, k.closeOver(attribute))
        assertEquals(j.rangeCount, k.getRangeCount())
        for (i in 0 until j.rangeCount) {
            assertEquals(j.getRangeStart(i), k.getRangeStart(i)); assertEquals(j.getRangeEnd(i), k.getRangeEnd(i))
        }
        assertEquals(j.strings().toList(), k.strings().toList())
    }
    @Test fun allSelectorsCodePointsAndRootStringMappingsMatchOriginal() {
        val strings = listOf("", "Straße", "ǳIJssel", "ΟΣ ΟΣΑ", "I\u0307", "A\u0301ß", "ﬁFoo", "don't STOP", "title:CASE-test",
            "ภาษาไทยABC", "မြန်မာABC", "日本語abc", "𐐀𐐨 Test", "\ud800A\udc00")
        for (str in strings) for (attribute in 0..31) {
            compare(J().add(str), UnicodeSet().add(str), attribute)
        }
        val random = Random(914802)
        repeat(400) {
            val j = J(); val k = UnicodeSet()
            repeat(random.nextInt(1, 100)) {
                val cp = random.nextInt(0x110000); j.add(cp); k.add(cp)
            }
            repeat(random.nextInt(5)) { val str = strings.random(random); j.add(str); k.add(str) }
            compare(j, k, random.nextInt(32))
        }
        val sensitive = J("[:Case_Sensitive:]")
        for (cp in sensitive) {
            // add(String) treats a single supplementary pair as one code point in both versions.
            compare(J().add(cp), UnicodeSet().add(cp), UnicodeSet.ADD_CASE_MAPPINGS)
        }
        compare(J(0, 0x10ffff), UnicodeSet(0, 0x10ffff), UnicodeSet.ADD_CASE_MAPPINGS)
        for (attribute in listOf(0, 2, 4, 6, 31)) {
            assertFailsWith<UnsupportedOperationException> { J().freeze().closeOver(attribute) }
            assertFailsWith<UnsupportedOperationException> { UnicodeSet().freeze().closeOver(attribute) }
        }
    }
}
