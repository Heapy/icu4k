// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StaticUnicodeSets
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.jvm.JvmStatic

open class IgnorablesMatcher private constructor(set: UnicodeSet?) : SymbolMatcher("", set), NumberParseMatcher.Flexible {
    override fun isDisabled(result: ParsedNumber?): Boolean = false
    override fun accept(segment: StringSegment?, result: ParsedNumber?) = Unit
    override fun toString(): String = "<IgnorablesMatcher>"
    companion object {
        private val DEFAULT = IgnorablesMatcher(StaticUnicodeSets.get(StaticUnicodeSets.Key.DEFAULT_IGNORABLES))
        private val STRICT = IgnorablesMatcher(StaticUnicodeSets.get(StaticUnicodeSets.Key.STRICT_IGNORABLES))
        private val JAVA_COMPATIBILITY = IgnorablesMatcher(StaticUnicodeSets.get(StaticUnicodeSets.Key.EMPTY))
        @JvmStatic fun getInstance(parseFlags: Int): IgnorablesMatcher = when {
            parseFlags and ParsingUtils.PARSE_FLAG_JAVA_COMPATIBILITY_IGNORABLES != 0 -> JAVA_COMPATIBILITY
            parseFlags and ParsingUtils.PARSE_FLAG_STRICT_IGNORABLES != 0 -> STRICT
            else -> DEFAULT
        }
    }
}
