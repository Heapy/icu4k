// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CollationLocaleOptionsTest {
    @Test fun attributesApplyToConfiguredEngine() {
        val f=ConfiguredCollatorFixture(CollationRoot.getRoot())
        CollationLocaleOptions.apply(ULocale("en@colStrength=primary;colCaseLevel=yes;colCaseFirst=upper;colNumeric=yes;colNormalization=yes;colReorder=Hani-Latn;kv=currency"),f.config)
        assertEquals(0,f.config.getStrength()); assertEquals(17,f.config.getDecomposition())
        assertTrue(f.config.isCaseLevel()); assertTrue(f.config.isUpperCaseFirst()); assertTrue(f.config.getNumericCollation())
        assertContentEquals(intArrayOf(17,25),f.config.getReorderCodes()); assertEquals(4099,f.config.getMaxVariable())
        assertEquals(1,f.core.doCompare("10","2"))
    }
    @Test fun partialMutationDeprecatedKeywordsAndAsciiParsing() {
        val f=ConfiguredCollatorFixture(CollationRoot.getRoot())
        assertFailsWith<IllegalArgumentException> { CollationLocaleOptions.apply(ULocale("en@colStrength=primary;colNumeric=maybe"),f.config) }
        assertEquals(0,f.config.getStrength())
        assertFailsWith<UnsupportedOperationException> { CollationLocaleOptions.apply(ULocale("en@colStrength=secondary;variableTop=x"),f.config) }
        assertEquals(0,f.config.getStrength())
        assertTrue(CollationLocaleOptions.equalIgnoreCase("YeS","yes"))
        assertFalse(CollationLocaleOptions.equalIgnoreCase("İ","i"))
        assertFalse(CollationLocaleOptions.equalIgnoreCase("Σ","σ"))
        assertEquals(4096,CollationLocaleOptions.getReorderCode("colReorder","SPACE"))
        assertFailsWith<IllegalArgumentException> { CollationLocaleOptions.getReorderCode("colReorder","others") }
    }
}
