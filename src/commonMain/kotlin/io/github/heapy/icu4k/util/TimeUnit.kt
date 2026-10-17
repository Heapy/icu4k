// Copyright (C) 2008-2014, Google, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.jvm.JvmStatic

open class TimeUnit internal constructor(type: String?, code: String?) : MeasureUnit(type, code) {
    // Retained only for streams written before the canonical unit proxy existed.
    private val index: Int = 0
    private fun readResolve(): Any = when (index) {
        6 -> SECOND
        5 -> MINUTE
        4 -> HOUR
        3 -> DAY
        2 -> WEEK
        1 -> MONTH
        0 -> YEAR
        else -> invalidTimeUnitIndex(index)
    }
    private fun writeReplace(): Any = measureUnitSerialProxy(type, subType)
    companion object {
        private const val serialVersionUID = -2839973855554750484L
        val SECOND get() = MeasureUnit.SECOND
        val MINUTE get() = MeasureUnit.MINUTE
        val HOUR get() = MeasureUnit.HOUR
        val DAY get() = MeasureUnit.DAY
        val WEEK get() = MeasureUnit.WEEK
        val MONTH get() = MeasureUnit.MONTH
        val YEAR get() = MeasureUnit.YEAR
        @JvmStatic fun values(): Array<TimeUnit> = arrayOf(SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR)
    }
}

internal expect fun invalidTimeUnitIndex(index: Int): Nothing
