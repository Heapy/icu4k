// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.stringSegmentCodePointAt
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.jvm.JvmStatic

class ParsingUtils {
    companion object {
        const val PARSE_FLAG_IGNORE_CASE = 0x0001
        const val PARSE_FLAG_MONETARY_SEPARATORS = 0x0002
        const val PARSE_FLAG_STRICT_SEPARATORS = 0x0004
        const val PARSE_FLAG_STRICT_GROUPING_SIZE = 0x0008
        const val PARSE_FLAG_INTEGER_ONLY = 0x0010
        const val PARSE_FLAG_GROUPING_DISABLED = 0x0020
        const val PARSE_FLAG_INCLUDE_UNPAIRED_AFFIXES = 0x0080
        const val PARSE_FLAG_USE_FULL_AFFIXES = 0x0100
        const val PARSE_FLAG_EXACT_AFFIX = 0x0200
        const val PARSE_FLAG_PLUS_SIGN_ALLOWED = 0x0400
        const val PARSE_FLAG_FORCE_BIG_DECIMAL = 0x1000
        const val PARSE_FLAG_NO_FOREIGN_CURRENCIES = 0x2000
        const val PARSE_FLAG_ALLOW_INFINITE_RECURSION = 0x4000
        const val PARSE_FLAG_STRICT_IGNORABLES = 0x8000
        const val PARSE_FLAG_JAVA_COMPATIBILITY_IGNORABLES = 0x10000
        @JvmStatic fun putLeadCodePoints(input: UnicodeSet?, output: UnicodeSet?) {
            for (range in input!!.ranges()) output!!.add(range.codepoint, range.codepointEnd)
            for (string in input.strings()) if (!string.isEmpty()) output!!.add(stringSegmentCodePointAt(string, 0))
        }
        @JvmStatic fun putLeadCodePoint(input: String?, output: UnicodeSet?) {
            if (!input!!.isEmpty()) output!!.add(stringSegmentCodePointAt(input, 0))
        }
    }
}
