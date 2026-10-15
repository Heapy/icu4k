// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.number.AffixPatternProvider
import io.github.heapy.icu4k.impl.number.AffixUtils
import io.github.heapy.icu4k.impl.number.PatternStringUtils
import io.github.heapy.icu4k.impl.number.PatternStringUtils.PatternSignType
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

open class AffixMatcher private constructor(private val prefix: AffixPatternMatcher?, private val suffix: AffixPatternMatcher?, private val flags: Int) : NumberParseMatcher {
    override fun match(segment: StringSegment?, result: ParsedNumber?): Boolean {
        if (!result!!.seenNumber()) {
            if (result.prefix != null || prefix == null) return false
            val initialOffset = segment!!.getOffset()
            val maybeMore = prefix.match(segment, result)
            if (initialOffset != segment.getOffset()) result.prefix = prefix.getPattern()
            return maybeMore
        } else {
            if (result.suffix != null || suffix == null || !matched(prefix, result.prefix)) return false
            val initialOffset = segment!!.getOffset()
            val maybeMore = suffix.match(segment, result)
            if (initialOffset != segment.getOffset()) result.suffix = suffix.getPattern()
            return maybeMore
        }
    }
    override fun smokeTest(segment: StringSegment?): Boolean = prefix != null && prefix.smokeTest(segment) || suffix != null && suffix.smokeTest(segment)
    override fun postProcess(result: ParsedNumber?) {
        if (matched(prefix, result!!.prefix) && matched(suffix, result.suffix)) {
            if (result.prefix == null) result.prefix = ""
            if (result.suffix == null) result.suffix = ""
            result.flags = result.flags or flags
            prefix?.postProcess(result); suffix?.postProcess(result)
        }
    }
    override fun equals(other: Any?): Boolean = other is AffixMatcher && equalObjects(prefix, other.prefix) && equalObjects(suffix, other.suffix) && flags == other.flags
    override fun hashCode(): Int = (prefix?.hashCode() ?: 0) xor (suffix?.hashCode() ?: 0) xor flags
    override fun toString(): String = "<AffixMatcher" + (if (flags and ParsedNumber.FLAG_NEGATIVE != 0) ":negative " else " ") + prefix + "#" + suffix + ">"
    companion object {
        private fun equalObjects(a: Any?, b: Any?): Boolean = a === b || a != null && a == b
        private fun length(matcher: AffixPatternMatcher?): Int = if (matcher == null) 0 else matcher.getPattern().length
        internal fun matched(affix: AffixPatternMatcher?, patternString: String?): Boolean = affix == null && patternString == null || affix != null && affix.getPattern() == patternString
        @JvmField val COMPARATOR: Comparator<AffixMatcher?> = Comparator { left, right ->
            val lhs = left!!; val rhs = right!!
            if (length(lhs.prefix) != length(rhs.prefix)) { if (length(lhs.prefix) > length(rhs.prefix)) -1 else 1 }
            else if (length(lhs.suffix) != length(rhs.suffix)) { if (length(lhs.suffix) > length(rhs.suffix)) -1 else 1 }
            else if (!lhs.equals(rhs)) { if (lhs.hashCode() > rhs.hashCode()) -1 else 1 }
            else 0
        }
        private fun isInteresting(patternInfo: AffixPatternProvider?, ignorables: IgnorablesMatcher?, parseFlags: Int): Boolean {
            val posPrefix = patternInfo!!.getString(AffixPatternProvider.FLAG_POS_PREFIX)
            val posSuffix = patternInfo.getString(AffixPatternProvider.FLAG_POS_SUFFIX)
            var negPrefix: String? = null; var negSuffix: String? = null
            if (patternInfo.hasNegativeSubpattern()) {
                negPrefix = patternInfo.getString(AffixPatternProvider.FLAG_NEG_PREFIX)
                negSuffix = patternInfo.getString(AffixPatternProvider.FLAG_NEG_SUFFIX)
            }
            return !(parseFlags and ParsingUtils.PARSE_FLAG_USE_FULL_AFFIXES == 0 &&
                AffixUtils.containsOnlySymbolsAndIgnorables(posPrefix, ignorables!!.getSet()) &&
                AffixUtils.containsOnlySymbolsAndIgnorables(posSuffix, ignorables.getSet()) &&
                AffixUtils.containsOnlySymbolsAndIgnorables(negPrefix, ignorables.getSet()) &&
                AffixUtils.containsOnlySymbolsAndIgnorables(negSuffix, ignorables.getSet()) &&
                !AffixUtils.containsType(posSuffix, AffixUtils.TYPE_PLUS_SIGN) && !AffixUtils.containsType(posSuffix, AffixUtils.TYPE_MINUS_SIGN) &&
                !AffixUtils.containsType(negSuffix, AffixUtils.TYPE_PLUS_SIGN) && !AffixUtils.containsType(negSuffix, AffixUtils.TYPE_MINUS_SIGN))
        }
        @JvmStatic fun createMatchers(patternInfo: AffixPatternProvider?, output: NumberParserImpl?, factory: AffixTokenMatcherFactory?, ignorables: IgnorablesMatcher?, parseFlags: Int) {
            if (!isInteresting(patternInfo, ignorables, parseFlags)) return
            val sb = StringBuilder()
            val matchers = ArrayList<AffixMatcher>(6)
            val includeUnpaired = parseFlags and ParsingUtils.PARSE_FLAG_INCLUDE_UNPAIRED_AFFIXES != 0
            var posPrefix: AffixPatternMatcher? = null; var posSuffix: AffixPatternMatcher? = null
            for (type in PatternSignType.VALUES) {
                if (type == PatternSignType.POS && parseFlags and ParsingUtils.PARSE_FLAG_PLUS_SIGN_ALLOWED != 0) continue
                if (type == PatternSignType.POS_SIGN && parseFlags and ParsingUtils.PARSE_FLAG_PLUS_SIGN_ALLOWED == 0) continue
                PatternStringUtils.patternInfoToStringBuilder(patternInfo, true, type, false, StandardPlural.OTHER, false, sb)
                val prefix = AffixPatternMatcher.fromAffixPattern(sb.toString(), factory, parseFlags)
                PatternStringUtils.patternInfoToStringBuilder(patternInfo, false, type, false, StandardPlural.OTHER, false, sb)
                val suffix = AffixPatternMatcher.fromAffixPattern(sb.toString(), factory, parseFlags)
                if (type == PatternSignType.POS) { posPrefix = prefix; posSuffix = suffix }
                else if (equalObjects(prefix, posPrefix) && equalObjects(suffix, posSuffix)) continue
                val flags = if (type == PatternSignType.NEG) ParsedNumber.FLAG_NEGATIVE else 0
                matchers.add(AffixMatcher(prefix, suffix, flags))
                if (includeUnpaired && prefix != null && suffix != null) {
                    if (type == PatternSignType.POS || !equalObjects(prefix, posPrefix)) matchers.add(AffixMatcher(prefix, null, flags))
                    if (type == PatternSignType.POS || !equalObjects(suffix, posSuffix)) matchers.add(AffixMatcher(null, suffix, flags))
                }
            }
            matchers.sortWith(COMPARATOR)
            output!!.addMatchers(matchers)
        }
    }
}
