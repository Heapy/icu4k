// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.DecimalQuantityCore
import kotlin.test.*

class IntegerWidthTest {
    @Test fun configurationIdentityAndBounds() {
        val default = IntegerWidth.zeroFillTo(1)
        assertSame(default, IntegerWidth.zeroFillTo(1))
        assertSame(default, default.truncateAt(-1))
        assertSame(default, default.truncateAt(3).truncateAt(-1))
        val width = IntegerWidth.zeroFillTo(3).truncateAt(5)
        assertEquals(3, width.minInt); assertEquals(5, width.maxInt)
        assertSame(width, width.truncateAt(5))
        assertFailsWith<IllegalArgumentException> { width.truncateAt(2) }
        assertFailsWith<IllegalArgumentException> { IntegerWidth.zeroFillTo(-1) }
        assertFailsWith<IllegalArgumentException> { IntegerWidth.zeroFillTo(1000) }
        val quantity = DecimalQuantityCore(12L).apply { setMinInteger(width.minInt) }
        assertEquals("012", quantity.toPlainString())
        assertEquals(999, IntegerWidth.zeroFillTo(999).truncateAt(999).maxInt)
    }
}
