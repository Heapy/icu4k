// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField

open class AffixTokenMatcherFactory {
    @JvmField var currency: Currency? = null
    @JvmField var symbols: DecimalFormatSymbols? = null
    @JvmField var ignorables: IgnorablesMatcher? = null
    @JvmField var locale: ULocale? = null
    @JvmField var parseFlags: Int = 0
    open fun minusSign(): MinusSignMatcher = MinusSignMatcher.getInstance(symbols, true)
    open fun plusSign(): PlusSignMatcher = PlusSignMatcher.getInstance(symbols, true)
    open fun approximatelySign(): ApproximatelySignMatcher = ApproximatelySignMatcher.getInstance(symbols, true)
    open fun percent(): PercentMatcher = PercentMatcher.getInstance(symbols)
    open fun permille(): PermilleMatcher = PermilleMatcher.getInstance(symbols)
    open fun currency(): CombinedCurrencyMatcher = CombinedCurrencyMatcher.getInstance(currency, symbols, parseFlags)
    open fun ignorables(): IgnorablesMatcher? = ignorables
}
