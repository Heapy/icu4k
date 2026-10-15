// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import kotlin.jvm.JvmStatic

/** Number notation; compact factories are added with the compact-data pipeline. */
open class Notation internal constructor() {
    companion object {
        private val SCIENTIFIC = ScientificNotation(1, false, 1, SignDisplay.AUTO)
        private val ENGINEERING = ScientificNotation(3, false, 1, SignDisplay.AUTO)
        private val SIMPLE = SimpleNotation()
        @JvmStatic fun scientific(): ScientificNotation = SCIENTIFIC
        @JvmStatic fun engineering(): ScientificNotation = ENGINEERING
        @JvmStatic fun simple(): SimpleNotation = SIMPLE
    }
}
