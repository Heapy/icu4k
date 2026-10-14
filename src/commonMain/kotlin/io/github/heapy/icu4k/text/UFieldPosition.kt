// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Fraction-digit metadata attached to a platform field position. */
@Deprecated("This API is ICU internal only.")
open class UFieldPosition : FormatFieldPosition {
    private var countVisibleFractionDigits = -1
    private var fractionDigits = 0L
    constructor() : super(-1)
    constructor(field: Int) : super(field)
    constructor(attribute: FormatField?, fieldID: Int) : super(attribute, fieldID)
    constructor(attribute: FormatField?) : super(attribute)
    open fun setFractionDigits(countVisibleFractionDigits: Int, fractionDigits: Long) {
        this.countVisibleFractionDigits = countVisibleFractionDigits; this.fractionDigits = fractionDigits
    }
    open fun getCountVisibleFractionDigits(): Int = countVisibleFractionDigits
    open fun getFractionDigits(): Long = fractionDigits
}
