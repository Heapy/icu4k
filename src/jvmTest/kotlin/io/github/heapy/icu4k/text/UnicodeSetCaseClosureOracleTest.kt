package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetCaseClosureOracleTest {
    private fun compare(j: JavaSet, k: UnicodeSet, simple: Boolean) {
        j.closeOver(if (simple) JavaSet.SIMPLE_CASE_INSENSITIVE else JavaSet.CASE_INSENSITIVE)
        k.closeOverCaseInsensitive(simple)
        assertEquals(j.getRangeCount(), k.getRangeCount())
        for (i in 0 until j.getRangeCount()) { assertEquals(j.getRangeStart(i), k.getRangeStart(i)); assertEquals(j.getRangeEnd(i), k.getRangeEnd(i)) }
        assertEquals(j.strings().toSet(), k.strings().toSet())
    }
    @Test fun sensitiveCodePointsMixedSetsAndOptimizationMatchOriginal() {
        val random = Random(732)
        val sensitive = io.github.heapy.icu4k.lang.CharacterProperties.getBinaryPropertySet(UProperty.CASE_SENSITIVE)
        for (simple in listOf(false, true)) {
            for (r in 0 until sensitive.getRangeCount()) for (c in sensitive.getRangeStart(r)..sensitive.getRangeEnd(r)) compare(JavaSet().add(c), UnicodeSet().add(c), simple)
            repeat(400) {
                val j = JavaSet(); val k = UnicodeSet()
                repeat(random.nextInt(1, 45)) {
                    val c = random.nextInt(0x110000); j.add(c); k.add(c)
                }
                for (s in listOf("SS", "Bc", "Fi", "İx", "Σς", "😀A", CharArray(1) { 0xd800.toChar() }.concatToString() + "A")) {
                    if (random.nextBoolean()) { j.add(s); k.add(s) }
                }
                compare(j, k, simple)
            }
            compare(JavaSet(0, 0x10ffff), UnicodeSet(0, 0x10ffff), simple)
        }
    }
}
