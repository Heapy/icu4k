// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale

/** ICU locale metadata on top of the platform formatting protocol. */
abstract class UFormat : UFormatBase() {
    typealias SpanField = FormatSpanField
    private var validLocale: ULocale? = null
    private var actualLocale: ULocale? = null
    fun getLocale(type: ULocale.Type?): ULocale? = if (type == ULocale.ACTUAL_LOCALE) actualLocale else validLocale
    internal fun setLocale(valid: ULocale?, actual: ULocale?) {
        if ((valid == null) != (actual == null)) throw IllegalArgumentException()
        validLocale = valid; actualLocale = actual
    }
    /** Copy metadata for typed copies on platforms without generic shallow cloning. */
    protected fun copyLocaleTo(other: UFormat) { other.validLocale = validLocale; other.actualLocale = actualLocale }
    private companion object { private const val serialVersionUID: Long = -4964390515840164416L }
}
