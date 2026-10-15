// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale

actual abstract class NumberFormatFactory actual protected constructor() {
    actual open fun visible(): Boolean = true
    actual abstract fun getSupportedLocaleNames(): Set<String>?
    // As in ICU's mutually delegating defaults, at least one overload must be overridden.
    actual open fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? = createFormat(loc, formatType)
    actual companion object {
        actual const val FORMAT_NUMBER = 0
        actual const val FORMAT_CURRENCY = 1
        actual const val FORMAT_PERCENT = 2
        actual const val FORMAT_SCIENTIFIC = 3
        actual const val FORMAT_INTEGER = 4
    }
}
actual abstract class SimpleNumberFormatFactory : NumberFormatFactory {
    private val localeNames: Set<String>
    private val visibleValue: Boolean
    actual constructor(locale: ULocale?) : this(locale, true)
    actual constructor(locale: ULocale?, visible: Boolean) {
        localeNames = numberFactoryLocaleNames(locale!!.getBaseName())
        visibleValue = visible
    }
    actual final override fun visible(): Boolean = visibleValue
    actual final override fun getSupportedLocaleNames(): Set<String> = localeNames
}
