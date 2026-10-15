// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.jvm.JvmStatic

open class TimeUnit internal constructor(type: String?, code: String?) : MeasureUnit(type, code) {
    private fun writeReplace(): Any = measureUnitSerialProxy(type, subType)
    companion object {
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
