// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CurrencyDisplayNames
import io.github.heapy.icu4k.util.ULocale

/** Currency's display dispatch, extracted pending the public Currency facade. */
internal object CurrencyNames {
    fun symbol(code: String?, locale: ULocale? = ULocale.getDefault(ULocale.Category.DISPLAY)): String? = name(code, locale, 0, null)
    fun displayName(code: String?, locale: ULocale? = ULocale.getDefault()): String? = name(code, locale, 1, null)
    fun name(code: String?, locale: ULocale?, style: Int, choice: BooleanArray?): String? {
        if (choice != null) checkedSetArrayElement(choice, 0, false)
        val names = CurrencyDisplayNames.getInstance(locale)
        return when (style) {
            0 -> names.getSymbol(code)
            1 -> names.getName(code)
            3 -> names.getNarrowSymbol(code)
            4 -> names.getFormalSymbol(code)
            5 -> names.getVariantSymbol(code)
            else -> throw IllegalArgumentException("bad name style: $style")
        }
    }
    fun name(code: String?, locale: ULocale?, style: Int, plural: String?, choice: BooleanArray?): String? {
        if (style != 2) return name(code, locale, style, choice)
        if (choice != null) checkedSetArrayElement(choice, 0, false)
        return CurrencyDisplayNames.getInstance(locale).getPluralName(code, plural)
    }
}
