package io.github.heapy.icu4k.impl

import kotlin.test.*

class MeasureUnitResourceCatalogTest {
    @Test fun catalogIncludesUnitsAndCurrenciesAndExcludesPatternGroups() {
        val pairs = mutableSetOf<Pair<String, String>>()
        MeasureUnitResourceCatalog.populate { type, name -> pairs.add(type to name) }
        assertTrue("length" to "meter" in pairs)
        assertTrue("duration" to "second" in pairs)
        assertTrue("currency" to "USD" in pairs)
        assertFalse(pairs.any { it.first in listOf("coordinate", "compound") })
        for (name in listOf("en", "root", "pool", "res_index")) assertTrue(ICUData.exists("${ICUData.ICU_UNIT_BASE_NAME}/$name.res"))
    }
    @Test fun callerFailurePropagatesAndEnumerationCanBeRepeated() {
        val failure = IllegalStateException("stop")
        assertSame(failure, assertFailsWith<IllegalStateException> { MeasureUnitResourceCatalog.populate { _, _ -> throw failure } })
        var count = 0
        MeasureUnitResourceCatalog.populate { _, _ -> count++ }
        assertTrue(count > 200)
    }
}
