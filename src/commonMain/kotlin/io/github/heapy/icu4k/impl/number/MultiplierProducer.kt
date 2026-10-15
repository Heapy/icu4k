// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

fun interface MultiplierProducer {
    fun getMultiplier(magnitude: Int): Int
}
