// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.DecimalSeparatorDisplay
import io.github.heapy.icu4k.number.IntegerWidth
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Measure
import io.github.heapy.icu4k.util.MeasureUnit
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField

/** Mutable pipeline result, cloned per quantity when configured as an immutable base. */
open class MicroProps(private val immutable: Boolean) : MicroPropsCloneBase(), MicroPropsGenerator {
    @JvmField var sign: SignDisplay? = null
    @JvmField var symbols: DecimalFormatSymbols? = null
    @JvmField var nsName: String? = null
    @JvmField var padding: Padder? = null
    @JvmField var decimal: DecimalSeparatorDisplay? = null
    @JvmField var integerWidth: IntegerWidth? = null
    @JvmField var modOuter: Modifier? = null
    @JvmField var modMiddle: Modifier? = null
    @JvmField var modInner: Modifier? = null
    @JvmField var rounder: Precision? = null
    @JvmField var grouping: Grouper? = null
    @JvmField var useCurrency: Boolean = false
    @JvmField var gender: String? = null
    @JvmField var currencyAsDecimal: String? = null
    @JvmField var outputUnit: MeasureUnit? = null
    @JvmField var mixedMeasures: List<Measure?>? = null
    @JvmField var indexOfQuantity: Int = -1
    @Volatile private var exhausted = false

    override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
        if (immutable) return clone()
        if (exhausted) throw AssertionError("Cannot re-use a mutable MicroProps in the quantity chain")
        exhausted = true
        return this
    }
    public override fun clone(): MicroProps {
        val host = cloneHost()
        if (host != null) return host as MicroProps
        return MicroProps(immutable).also {
            it.sign = sign; it.symbols = symbols; it.nsName = nsName; it.padding = padding
            it.decimal = decimal; it.integerWidth = integerWidth
            it.modOuter = modOuter; it.modMiddle = modMiddle; it.modInner = modInner
            it.rounder = rounder; it.grouping = grouping; it.useCurrency = useCurrency
            it.gender = gender; it.currencyAsDecimal = currencyAsDecimal; it.outputUnit = outputUnit
            it.mixedMeasures = mixedMeasures; it.indexOfQuantity = indexOfQuantity; it.exhausted = exhausted
        }
    }
}

/** Host cloning without a serialization marker; Native supplies the known-field copy path above. */
expect abstract class MicroPropsCloneBase() {
    protected abstract fun clone(): Any
    protected fun cloneHost(): Any?
}
