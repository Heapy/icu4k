// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** A numeric amount and its unit. The supplied Number object is retained. */
open class Measure private constructor(number: Number?, unit: MeasureUnit?, private val numberKind: Int) {
    private val number: Number
    private val unit: MeasureUnit
    init {
        if (number == null || unit == null) throw NullPointerException("Number and MeasureUnit must not be null")
        this.number = measureInputNumber(number, numberKind)
        this.unit = unit
    }
    constructor(number: Number?, unit: MeasureUnit?) : this(number, unit, 0)
    // Typed paths preserve floating input metadata on JavaScript, where numeric boxes are erased.
    constructor(number: Double, unit: MeasureUnit?) : this(number, unit, 1)
    constructor(number: Float, unit: MeasureUnit?) : this(number, unit, 2)
    open fun getNumber(): Number = number
    open fun getUnit(): MeasureUnit = unit
    override fun equals(other: Any?): Boolean = this === other || other is Measure && unit == other.unit &&
        measureNumbersEqual(number, other.number, numberKind, other.numberKind)
    override fun hashCode(): Int {
        val bits = number.toDouble().toBits()
        return 31 * (bits xor (bits ushr 32)).toInt() + unit.hashCode()
    }
    override fun toString(): String = measureNumberText(number, numberKind) + ' ' + unit.toString()
}
internal expect fun measureNumberText(number: Number, kind: Int): String
internal expect fun measureNumbersEqual(first: Number, second: Number, firstKind: Int, secondKind: Int): Boolean

internal expect fun measureInputNumber(number: Number, kind: Int): Number
