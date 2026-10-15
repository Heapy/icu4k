package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CompactDataTest {
    @Test fun customAndResourcePatterns() {
        val rules = PluralRules.forLocale(ULocale("en"))
        val custom = CompactData()
        custom.populate(mapOf("1000" to mapOf("other" to "0K", "=1" to "one thousand")))
        assertEquals(-3, custom.getMultiplier(3))
        assertEquals("one thousand", custom.getPattern(3, rules, DecimalQuantity_DualStorageBCD(1)))
        assertEquals("0K", custom.getPattern(3, rules, DecimalQuantity_DualStorageBCD(2)))
        val resource = CompactData()
        resource.populate(ULocale("en"), "missing", CompactStyle.SHORT, CompactData.CompactType.DECIMAL)
        assertEquals(-3, resource.getMultiplier(3))
        assertEquals("0K", resource.getPattern(3, rules, DecimalQuantity_DualStorageBCD(2)))
        val patterns = mutableSetOf<String>()
        resource.getUniquePatterns(patterns)
        assertTrue("0K" in patterns)
        assertFalse("<USE FALLBACK>" in patterns)
    }
}
