// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

abstract class FractionPrecision internal constructor() : Precision() {
    open fun withSignificantDigits(minSignificantDigits: Int, maxSignificantDigits: Int, priority: RoundingPriority?): Precision {
        require(maxSignificantDigits >= 1 && maxSignificantDigits >= minSignificantDigits && maxSignificantDigits <= 999) { SIGNIFICANT_ERROR }
        return constructFractionSignificant(this, minSignificantDigits, maxSignificantDigits, priority, false)
    }
    open fun withMinDigits(minSignificantDigits: Int): Precision {
        require(minSignificantDigits in 1..999) { SIGNIFICANT_ERROR }
        return constructFractionSignificant(this, 1, minSignificantDigits, RoundingPriority.RELAXED, true)
    }
    open fun withMaxDigits(maxSignificantDigits: Int): Precision {
        require(maxSignificantDigits in 1..999) { SIGNIFICANT_ERROR }
        return constructFractionSignificant(this, 1, maxSignificantDigits, RoundingPriority.STRICT, true)
    }
}
