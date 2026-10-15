// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.text.CompactStyle
import kotlin.jvm.JvmStatic

/** Number notation factories. */
open class Notation internal constructor() {
    companion object {
        private val SCIENTIFIC = ScientificNotation(1, false, 1, SignDisplay.AUTO)
        private val ENGINEERING = ScientificNotation(3, false, 1, SignDisplay.AUTO)
        private val SIMPLE = SimpleNotation()
        private val COMPACT_SHORT = CompactNotation(CompactStyle.SHORT)
        private val COMPACT_LONG = CompactNotation(CompactStyle.LONG)
        @JvmStatic fun scientific(): ScientificNotation = SCIENTIFIC
        @JvmStatic fun engineering(): ScientificNotation = ENGINEERING
        @JvmStatic fun simple(): SimpleNotation = SIMPLE
        @JvmStatic fun compactShort(): CompactNotation = COMPACT_SHORT
        @JvmStatic fun compactLong(): CompactNotation = COMPACT_LONG
    }
}
