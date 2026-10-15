// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.jvm.JvmStatic

open class PaddingMatcher private constructor(symbol: String?) : SymbolMatcher(symbol, UnicodeSet.EMPTY), NumberParseMatcher.Flexible {
    override fun isDisabled(result: ParsedNumber?): Boolean = false
    override fun accept(segment: StringSegment?, result: ParsedNumber?) = Unit
    override fun toString(): String = "<PaddingMatcher>"
    companion object { @JvmStatic fun getInstance(padString: String?): PaddingMatcher = PaddingMatcher(padString) }
}
