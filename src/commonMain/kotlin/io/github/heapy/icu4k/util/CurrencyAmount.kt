// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

open class CurrencyAmount : Measure {
    constructor(number: Number?, currency: Currency?) : super(number, currency)
    constructor(number: Double, currency: Currency?) : super(number, currency)
    constructor(number: Float, currency: Currency?) : super(number, currency)
    open fun getCurrency(): Currency = getUnit() as Currency
}
