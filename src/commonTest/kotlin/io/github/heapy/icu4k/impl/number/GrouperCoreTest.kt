// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class GrouperCoreTest {
    private fun resolved(strategy: GrouperCore.GroupingStrategy, locale: String, pattern: String) =
        GrouperCore.forStrategy(strategy).withLocaleData(ULocale(locale), PatternStringParserCore.parseToPatternInfo(pattern))
    @Test fun westernIndicMinimumAndAlignedGrouping() {
        val western = resolved(GrouperCore.GroupingStrategy.AUTO, "en", "#,##0")
        assertTrue(western.groupAtPosition(3, DecimalQuantityCore(1000L)))
        assertFalse(western.groupAtPosition(2, DecimalQuantityCore(1000L)))
        val min2 = resolved(GrouperCore.GroupingStrategy.MIN2, "en", "#,##0")
        assertFalse(min2.groupAtPosition(3, DecimalQuantityCore(1000L)))
        assertTrue(min2.groupAtPosition(3, DecimalQuantityCore(10000L)))
        val spanish = resolved(GrouperCore.GroupingStrategy.AUTO, "es", "#,##0")
        assertFalse(spanish.groupAtPosition(3, DecimalQuantityCore(1000L)))
        val indic = resolved(GrouperCore.GroupingStrategy.AUTO, "en", "#,##,##0")
        assertEquals(3.toShort(), indic.getPrimary())
        assertEquals(2.toShort(), indic.getSecondary())
        assertTrue(indic.groupAtPosition(5, DecimalQuantityCore(1000000L)))
        assertFalse(indic.groupAtPosition(6, DecimalQuantityCore(1000000L)))
        assertFalse(resolved(GrouperCore.GroupingStrategy.AUTO, "en", "0").groupAtPosition(3, null))
        assertTrue(resolved(GrouperCore.GroupingStrategy.ON_ALIGNED, "en", "0").groupAtPosition(3, DecimalQuantityCore(1000L)))
    }
    @Test fun canonicalInstancesAndDisplayPadding() {
        val one = GrouperCore.getInstance(3, 3, 1)
        assertSame(one, GrouperCore.forStrategy(GrouperCore.GroupingStrategy.THOUSANDS))
        assertSame(one, one.withLocaleData(null, null))
        val value = DecimalQuantityCore(1L).apply { setMinInteger(4) }
        assertTrue(one.groupAtPosition(3, value))
        assertSame(GrouperCore.forStrategy(GrouperCore.GroupingStrategy.OFF), GrouperCore.forProperties(false, 3, 3, 1))
        assertEquals(2.toShort(), GrouperCore.forProperties(true, 0, 2, 1).getPrimary())
        assertEquals(2.toShort(), GrouperCore.forProperties(true, 2, 0, 1).getSecondary())
    }
}
