// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment
import kotlin.jvm.JvmStatic

open class CodePointMatcher private constructor(private val cp: Int) : NumberParseMatcher {
    override fun match(segment: StringSegment?, result: ParsedNumber?): Boolean {
        if (segment!!.startsWith(cp)) { segment.adjustOffsetByCodePoint(); result!!.setCharsConsumed(segment) }
        return false
    }
    override fun smokeTest(segment: StringSegment?): Boolean = segment!!.startsWith(cp)
    override fun postProcess(result: ParsedNumber?) = Unit
    override fun toString(): String = "<CodePointMatcher U+" + cp.toUInt().toString(16) + ">"
    companion object { @JvmStatic fun getInstance(cp: Int): CodePointMatcher = CodePointMatcher(cp) }
}
