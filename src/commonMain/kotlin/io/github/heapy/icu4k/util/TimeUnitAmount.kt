// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

open class TimeUnitAmount : Measure {
    constructor(number: Number?, unit: TimeUnit?) : super(number, unit)
    constructor(number: Double, unit: TimeUnit?) : super(number, unit)
    constructor(number: Float, unit: TimeUnit?) : super(number, unit)
    open fun getTimeUnit(): TimeUnit = getUnit() as TimeUnit
}
