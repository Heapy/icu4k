// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.GroupingStrategy
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class GrouperCoreTest {
    private fun resolved(strategy: GroupingStrategy, locale: String, pattern: String) =
        GrouperCore.forStrategy(strategy).withLocaleData(ULocale(locale), PatternStringParserCore.parseToPatternInfo(pattern))
    @Test fun westernIndicMinimumAndAlignedGrouping() {
        val western = resolved(GroupingStrategy.AUTO, "en", "#,##0")
        assertTrue(western.groupAtPosition(3, DecimalQuantity_DualStorageBCD(1000L)))
        assertFalse(western.groupAtPosition(2, DecimalQuantity_DualStorageBCD(1000L)))
        val min2 = resolved(GroupingStrategy.MIN2, "en", "#,##0")
        assertFalse(min2.groupAtPosition(3, DecimalQuantity_DualStorageBCD(1000L)))
        assertTrue(min2.groupAtPosition(3, DecimalQuantity_DualStorageBCD(10000L)))
        val spanish = resolved(GroupingStrategy.AUTO, "es", "#,##0")
        assertFalse(spanish.groupAtPosition(3, DecimalQuantity_DualStorageBCD(1000L)))
        val indic = resolved(GroupingStrategy.AUTO, "en", "#,##,##0")
        assertEquals(3.toShort(), indic.getPrimary())
        assertEquals(2.toShort(), indic.getSecondary())
        assertTrue(indic.groupAtPosition(5, DecimalQuantity_DualStorageBCD(1000000L)))
        assertFalse(indic.groupAtPosition(6, DecimalQuantity_DualStorageBCD(1000000L)))
        assertFalse(resolved(GroupingStrategy.AUTO, "en", "0").groupAtPosition(3, null))
        assertTrue(resolved(GroupingStrategy.ON_ALIGNED, "en", "0").groupAtPosition(3, DecimalQuantity_DualStorageBCD(1000L)))
    }
    @Test fun canonicalInstancesAndDisplayPadding() {
        val one = GrouperCore.getInstance(3, 3, 1)
        assertSame(one, GrouperCore.forStrategy(GroupingStrategy.THOUSANDS))
        assertSame(one, one.withLocaleData(null, null))
        val value = DecimalQuantity_DualStorageBCD(1L).apply { setMinInteger(4) }
        assertTrue(one.groupAtPosition(3, value))
        assertSame(GrouperCore.forStrategy(GroupingStrategy.OFF), GrouperCore.forProperties(false, 3, 3, 1))
        assertEquals(2.toShort(), GrouperCore.forProperties(true, 0, 2, 1).getPrimary())
        assertEquals(2.toShort(), GrouperCore.forProperties(true, 2, 0, 1).getSecondary())
    }
}
