// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment
import kotlin.jvm.JvmField

open class SeriesMatcher : NumberParseMatcher {
    @JvmField protected var matchers: MutableList<NumberParseMatcher?>? = null
    @JvmField protected var frozen = false
    open fun addMatcher(matcher: NumberParseMatcher?) {
        assertSeriesMatcher { !frozen }
        if (matchers == null) matchers = ArrayList()
        matchers!!.add(matcher)
    }
    open fun freeze() { frozen = true }
    open fun length(): Int = matchers?.size ?: 0
    override fun match(segment: StringSegment?, result: ParsedNumber?): Boolean {
        assertSeriesMatcher { frozen }
        if (matchers == null) return false
        val backup = ParsedNumber(); backup.copyFrom(result)
        val initialOffset = segment!!.getOffset()
        var maybeMore = true
        var i = 0
        while (i < matchers!!.size) {
            val matcher = matchers!![i]
            val matcherOffset = segment.getOffset()
            maybeMore = if (segment.length != 0) matcher!!.match(segment, result) else true
            val success = segment.getOffset() != matcherOffset
            val flexible = matcher is NumberParseMatcher.Flexible
            if (success && flexible) {
                // Re-run a successful flexible matcher.
            } else if (success) {
                i++
                if (i < matchers!!.size && segment.getOffset() != result!!.charEnd && result.charEnd > matcherOffset) segment.setOffset(result.charEnd)
            } else if (flexible) i++
            else { segment.setOffset(initialOffset); result!!.copyFrom(backup); return maybeMore }
        }
        return maybeMore
    }
    override fun smokeTest(segment: StringSegment?): Boolean {
        assertSeriesMatcher { frozen }
        if (matchers == null) return false
        assertSeriesMatcher { matchers!![0] !is NumberParseMatcher.Flexible }
        return matchers!![0]!!.smokeTest(segment)
    }
    override fun postProcess(result: ParsedNumber?) {
        assertSeriesMatcher { frozen }
        if (matchers == null) return
        var i = 0
        while (i < matchers!!.size) { val matcher = matchers!![i]; matcher!!.postProcess(result); i++ }
    }
    override fun toString(): String = "<SeriesMatcher $matchers>"
}
internal expect fun assertSeriesMatcher(condition: () -> Boolean)
