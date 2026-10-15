// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

/** A quantity-dependent stage in the number formatter pipeline. */
fun interface MicroPropsGenerator {
    fun processQuantity(quantity: DecimalQuantity?): MicroProps?
}
