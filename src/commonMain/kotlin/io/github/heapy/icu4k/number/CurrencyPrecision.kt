// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.util.Currency

abstract class CurrencyPrecision internal constructor() : Precision() {
    open fun withCurrency(currency: Currency?): Precision {
        require(currency != null) { "Currency must not be null" }
        val result = constructFromCurrency(this, currency)
        result.trailingZeroDisplay = trailingZeroDisplay
        return result
    }
}
