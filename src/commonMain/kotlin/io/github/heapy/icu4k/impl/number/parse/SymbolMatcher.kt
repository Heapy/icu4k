// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StaticUnicodeSets
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.jvm.JvmField

abstract class SymbolMatcher : NumberParseMatcher {
    @JvmField protected val string: String?
    @JvmField protected val uniSet: UnicodeSet?
    protected constructor(symbolString: String?, symbolSet: UnicodeSet?) { string = symbolString; uniSet = symbolSet }
    protected constructor(key: StaticUnicodeSets.Key?) { string = ""; uniSet = StaticUnicodeSets.get(key) }
    open fun getSet(): UnicodeSet? = uniSet
    override fun match(segment: StringSegment?, result: ParsedNumber?): Boolean {
        if (isDisabled(result)) return false
        var overlap = 0
        if (!string!!.isEmpty()) {
            overlap = segment!!.getCommonPrefixLength(string)
            if (overlap == string.length) {
                segment.adjustOffset(string.length); accept(segment, result); return false
            }
        }
        if (segment!!.startsWith(uniSet)) {
            segment.adjustOffsetByCodePoint(); accept(segment, result); return false
        }
        return overlap == segment.length
    }
    override fun smokeTest(segment: StringSegment?): Boolean = segment!!.startsWith(uniSet) || segment.startsWith(string)
    override fun postProcess(result: ParsedNumber?) = Unit
    protected abstract fun isDisabled(result: ParsedNumber?): Boolean
    protected abstract fun accept(segment: StringSegment?, result: ParsedNumber?)
}
