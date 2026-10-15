// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.MacroProps
import io.github.heapy.icu4k.impl.number.MicroProps
import kotlin.concurrent.Volatile

/** Shared lazy compilation policy, including source resolver calls and compilation-failure behavior. */
internal class NumberFormatterExecution(private val resolve: () -> MacroProps) {
    constructor(macros: MacroProps) : this({ macros })
    private val countLock = createAvailabilityLock()
    private var count = 0L
    @Volatile private var compiled: NumberFormatterImpl? = null
    private fun computeCompiled(): Boolean {
        val macros = resolve()
        val current = countLock.withLock { ++count }
        if (current == macros.threshold!!) { compiled = NumberFormatterImpl(macros); return true }
        return compiled != null
    }
    fun formatImpl(value: DecimalQuantity?, output: FormattedStringBuilder?): MicroProps =
        if (computeCompiled()) compiled!!.format(value, output) else NumberFormatterImpl.formatStatic(resolve(), value, output)
    fun getPrefixSuffix(sign: Byte, plural: StandardPlural?, output: FormattedStringBuilder?): Int =
        if (computeCompiled()) compiled!!.getPrefixSuffix(sign, plural, output) else NumberFormatterImpl.getPrefixSuffixStatic(resolve(), sign, plural, output)
}
