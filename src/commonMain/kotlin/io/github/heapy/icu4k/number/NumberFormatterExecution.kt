// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.MacroProps
import io.github.heapy.icu4k.impl.number.MicroProps
import kotlin.concurrent.Volatile

/** LocalizedNumberFormatter's execution/cache core, also used for mixed-unit integers. */
internal class NumberFormatterExecution(private val macros: MacroProps) {
    private val countLock = createAvailabilityLock()
    private var count = 0L
    @Volatile private var compiled: NumberFormatterImpl? = null
    fun formatImpl(value: DecimalQuantity?, output: FormattedStringBuilder?): MicroProps {
        val current = countLock.withLock { ++count }
        if (current == macros.threshold!!) {
            compiled = NumberFormatterImpl(macros)
            return compiled!!.format(value, output)
        }
        if (compiled != null) return compiled!!.format(value, output)
        return NumberFormatterImpl.formatStatic(macros, value, output)
    }
}
