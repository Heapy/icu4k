// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import kotlin.concurrent.Volatile

open class LocalizedNumberRangeFormatter internal constructor(parent: NumberRangeFormatterSettings<*>?, key: Int, value: Any?) :
    NumberRangeFormatterSettings<LocalizedNumberRangeFormatter>(parent, key, value) {
    @Volatile private var impl: NumberRangeFormatterImpl? = null
    open fun formatRange(first: Int, second: Int): FormattedNumberRange {
        val a = DecimalQuantity_DualStorageBCD(first); val b = DecimalQuantity_DualStorageBCD(second)
        return formatImpl(a, b, first == second)
    }
    open fun formatRange(first: Double, second: Double): FormattedNumberRange {
        val a = DecimalQuantity_DualStorageBCD(first); val b = DecimalQuantity_DualStorageBCD(second)
        return formatImpl(a, b, first == second)
    }
    open fun formatRange(first: Number?, second: Number?): FormattedNumberRange {
        if (first == null || second == null) throw IllegalArgumentException("Cannot format null values in range")
        val a = DecimalQuantity_DualStorageBCD(first); val b = DecimalQuantity_DualStorageBCD(second)
        return formatImpl(a, b, first.equals(second))
    }
    open fun withoutLocale(): UnlocalizedNumberRangeFormatter = UnlocalizedNumberRangeFormatter(this, KEY_LOCALE, null)
    internal open fun formatImpl(first: DecimalQuantity?, second: DecimalQuantity?, equalBefore: Boolean): FormattedNumberRange {
        if (impl == null) impl = NumberRangeFormatterImpl(resolve())
        return impl!!.format(first, second, equalBefore)
    }
    internal override fun create(key: Int, value: Any?): LocalizedNumberRangeFormatter = LocalizedNumberRangeFormatter(this, key, value)
}
