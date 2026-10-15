// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.number.Scale

open class MultiplierParseHandler(private val multiplier: Scale?) : ValidationMatcher() {
    override fun postProcess(result: ParsedNumber?) { if (result!!.quantity != null) multiplier!!.applyReciprocalTo(result.quantity) }
    override fun toString(): String = "<MultiplierHandler $multiplier>"
}
