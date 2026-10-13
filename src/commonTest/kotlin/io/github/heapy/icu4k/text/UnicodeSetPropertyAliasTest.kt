package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetPropertyAliasTest {
    @Test fun namedNumericAgeAndSpecialAliases() {
        val set = UnicodeSet().add("old")
        assertSame(set, set.applyPropertyAlias("gc", "Lu"))
        assertTrue(set.contains(65)); assertFalse(set.contains(97)); assertFalse(set.contains("old"))
        set.applyPropertyAlias("scx", "Latn"); assertTrue(set.contains(65))
        set.applyPropertyAlias("ccc", " 230 "); assertTrue(set.contains(0x301))
        set.applyPropertyAlias("nv", "0x1p-1"); assertTrue(set.contains(0xbd))
        set.applyPropertyAlias("name", " LATIN  CAPITAL\tLETTER A "); assertEquals(UnicodeSet(65, 65), set)
        set.applyPropertyAlias("age", "1.1"); assertTrue(set.contains(65)); assertFalse(set.contains(0x1f600))
        set.applyPropertyAlias("ASCII", null); assertEquals(UnicodeSet(0, 127), set)
        set.applyPropertyAlias("Any", ""); assertTrue(set.contains(0, 0x10ffff))
        set.applyPropertyAlias("Assigned", ""); assertTrue(set.contains(65)); assertFalse(set.contains(0x378))
    }
    @Test fun explicitAndDefaultSymbols() {
        val custom = object : UnicodeSet.XSymbolTable() {
            override fun applyPropertyAlias(propertyName: String, propertyValue: String?, result: UnicodeSet): Boolean {
                if (propertyName != "custom") return false
                result.clear().add(if (propertyValue == null) 65 else 66)
                return true
            }
        }
        val set = UnicodeSet()
        set.applyPropertyAlias("custom", null, custom); assertTrue(set.contains(65))
        UnicodeSet.setDefaultXSymbolTable(custom)
        try {
            assertSame(custom, UnicodeSet.getDefaultXSymbolTable())
            set.applyPropertyAlias("custom", ""); assertTrue(set.contains(66))
        } finally { UnicodeSet.setDefaultXSymbolTable(null) }
        assertFailsWith<IllegalArgumentException> { set.applyPropertyAlias("custom", "") }
    }
}
