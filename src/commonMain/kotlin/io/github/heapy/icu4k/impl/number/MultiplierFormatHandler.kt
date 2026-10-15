// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.Scale
import kotlin.jvm.JvmField

/** A scale stage applied after its parent resolves the MicroProps. */
open class MultiplierFormatHandler(@JvmField internal val multiplier: Scale?,
    @JvmField internal val parent: MicroPropsGenerator?) : MicroPropsGenerator {
    override fun processQuantity(quantity: DecimalQuantity?): MicroProps? {
        val micros = parent!!.processQuantity(quantity)
        multiplier!!.applyTo(quantity)
        return micros
    }
}
