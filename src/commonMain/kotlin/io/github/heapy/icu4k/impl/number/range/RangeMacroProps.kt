// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.range

import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeCollapse
import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeIdentityFallback
import io.github.heapy.icu4k.number.UnlocalizedNumberFormatter
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField

open class RangeMacroProps {
    @JvmField var formatter1: UnlocalizedNumberFormatter? = null
    @JvmField var formatter2: UnlocalizedNumberFormatter? = null
    @JvmField var sameFormatters: Int = -1
    @JvmField var collapse: RangeCollapse? = null
    @JvmField var identityFallback: RangeIdentityFallback? = null
    @JvmField var loc: ULocale? = null
    override fun hashCode(): Int = arrayOf(formatter1, formatter2, collapse, identityFallback, loc).contentHashCode()
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is RangeMacroProps) return false
        return equal(formatter1, other.formatter1) && equal(formatter2, other.formatter2) &&
            equal(collapse, other.collapse) && equal(identityFallback, other.identityFallback) && equal(loc, other.loc)
    }
    private fun equal(a: Any?, b: Any?): Boolean = a === b || (a != null && a.equals(b))
}
