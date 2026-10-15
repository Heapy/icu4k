// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import io.github.heapy.icu4k.util.toLocale
import java.util.Collections
import java.util.Locale

actual abstract class NumberFormatFactory actual protected constructor() {
    actual open fun visible(): Boolean = true
    actual abstract fun getSupportedLocaleNames(): Set<String>?
    actual open fun createFormat(loc: ULocale?, formatType: Int): NumberFormat? = createFormat(loc!!.toLocale(), formatType)
    open fun createFormat(loc: Locale?, formatType: Int): NumberFormat? = createFormat(ULocale.forLocale(loc), formatType)
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
        localeNames = Collections.singleton(locale!!.getBaseName())
        visibleValue = visible
    }
    constructor(locale: Locale?) : this(locale, true)
    constructor(locale: Locale?, visible: Boolean) {
        localeNames = Collections.singleton(ULocale.forLocale(locale)!!.getBaseName())
        visibleValue = visible
    }
    actual final override fun visible(): Boolean = visibleValue
    actual final override fun getSupportedLocaleNames(): Set<String> = localeNames
}
