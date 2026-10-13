package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.test.*

class UnicodeSetHelpersOracleTest {
    @Test fun recognitionAndCollectionHelpersMatchOriginal() {
        for (pattern in listOf("", "[", "[]", "[a]", "\\p", "\\p{L}", "\\P{L}", "\\N{x}", "\\n{x}", "abc[:x:]")) for (pos in 0..pattern.length + 2) assertEquals(JavaSet.resemblesPattern(pattern, pos), UnicodeSet.resemblesPattern(pattern, pos))
        val lists = listOf(emptyList(), listOf("a"), listOf("z"), listOf("a", "b"))
        for (a in lists) for (b in lists) for (style in UnicodeSet.ComparisonStyle.entries) assertEquals(JavaSet.compare(a, b, JavaSet.ComparisonStyle.valueOf(style.name)), UnicodeSet.compare(a, b, style))
        val a = arrayOf("", "", "tail"); val b = a.copyOf()
        assertContentEquals(JavaSet.addAllTo(listOf("x", "y"), a), UnicodeSet.addAllTo(listOf("x", "y"), b))
    }
}
