// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import kotlin.jvm.JvmStatic

open class RequireDecimalSeparatorValidator private constructor(private val patternHasDecimalSeparator: Boolean) : ValidationMatcher() {
    override fun postProcess(result: ParsedNumber?) {
        val special = result!!.flags and ParsedNumber.FLAG_INFINITY != 0 || result.flags and ParsedNumber.FLAG_NAN != 0
        val decimal = result.flags and ParsedNumber.FLAG_HAS_DECIMAL_SEPARATOR != 0
        if (!special && decimal != patternHasDecimalSeparator) result.flags = result.flags or ParsedNumber.FLAG_FAIL
    }
    override fun toString(): String = "<RequireDecimalSeparator>"
    companion object {
        private val A = RequireDecimalSeparatorValidator(true)
        private val B = RequireDecimalSeparatorValidator(false)
        @JvmStatic fun getInstance(patternHasDecimalSeparator: Boolean): RequireDecimalSeparatorValidator = if (patternHasDecimalSeparator) A else B
    }
}
