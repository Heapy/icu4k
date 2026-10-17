// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.lang.UCharacter

/** A case-preserving string key compared using Unicode default full case folding. */
open class CaseInsensitiveString(private val string: String?) {
    private var hash = 0
    private var folded: String? = null
    private fun getFolded(): String {
        if (folded == null) folded = UCharacter.foldCase(string!!, true)
        return folded!!
    }
    open fun getString(): String? = string
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is CaseInsensitiveString) return false
        return getFolded() == other.getFolded()
    }
    override fun hashCode(): Int {
        val value = getFolded()
        if (hash == 0) hash = value.hashCode()
        return hash
    }
    // Java permits this method to return null when the original constructor argument was null.
    override fun toString(): String = sourceStringValue(string)
    @Suppress("UNCHECKED_CAST")
    private fun <T> sourceStringValue(value: Any?): T = value as T
}
