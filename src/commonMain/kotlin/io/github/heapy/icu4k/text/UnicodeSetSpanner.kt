// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.OutputInt
import io.github.heapy.icu4k.text.UnicodeSet.SpanCondition

/** Counts, replaces and trims spans. Retains the source set, which may be thawed. */
open class UnicodeSetSpanner(private val unicodeSet: UnicodeSet) {
    open fun getUnicodeSet(): UnicodeSet = unicodeSet
    override fun equals(other: Any?): Boolean = other is UnicodeSetSpanner && unicodeSet == other.unicodeSet
    override fun hashCode(): Int = unicodeSet.hashCode()
    enum class CountMethod { WHOLE_SPAN, MIN_ELEMENTS }
    enum class TrimOption { LEADING, BOTH, TRAILING }
    open fun countIn(sequence: CharSequence): Int = countIn(sequence, CountMethod.MIN_ELEMENTS, SpanCondition.SIMPLE)
    open fun countIn(sequence: CharSequence, countMethod: CountMethod): Int = countIn(sequence, countMethod, SpanCondition.SIMPLE)
    open fun countIn(sequence: CharSequence, countMethod: CountMethod, spanCondition: SpanCondition): Int {
        var count = 0
        var start = 0
        val skipSpan = if (spanCondition == SpanCondition.NOT_CONTAINED) SpanCondition.SIMPLE else SpanCondition.NOT_CONTAINED
        val length = sequence.length
        var spanCount: OutputInt? = null
        while (start != length) {
            val endOfSpan = unicodeSet.span(sequence, start, skipSpan)
            if (endOfSpan == length) break
            if (countMethod == CountMethod.WHOLE_SPAN) {
                start = unicodeSet.span(sequence, endOfSpan, spanCondition)
                ++count
            } else {
                if (spanCount == null) spanCount = OutputInt()
                start = unicodeSet.spanAndCount(sequence, endOfSpan, spanCondition, spanCount)
                count += spanCount.value
            }
        }
        return count
    }
    open fun deleteFrom(sequence: CharSequence): String = replaceFrom(sequence, "", CountMethod.WHOLE_SPAN, SpanCondition.SIMPLE)
    open fun deleteFrom(sequence: CharSequence, spanCondition: SpanCondition): String = replaceFrom(sequence, "", CountMethod.WHOLE_SPAN, spanCondition)
    open fun replaceFrom(sequence: CharSequence, replacement: CharSequence): String = replaceFrom(sequence, replacement, CountMethod.MIN_ELEMENTS, SpanCondition.SIMPLE)
    open fun replaceFrom(sequence: CharSequence, replacement: CharSequence, countMethod: CountMethod): String = replaceFrom(sequence, replacement, countMethod, SpanCondition.SIMPLE)
    open fun replaceFrom(sequence: CharSequence, replacement: CharSequence, countMethod: CountMethod, spanCondition: SpanCondition): String {
        val copySpan = if (spanCondition == SpanCondition.NOT_CONTAINED) SpanCondition.SIMPLE else SpanCondition.NOT_CONTAINED
        val remove = replacement.isEmpty()
        val result = StringBuilder()
        val length = sequence.length
        var spanCount: OutputInt? = null
        var endCopy = 0
        while (endCopy != length) {
            val endModify = if (countMethod == CountMethod.WHOLE_SPAN) unicodeSet.span(sequence, endCopy, spanCondition)
            else {
                if (spanCount == null) spanCount = OutputInt()
                unicodeSet.spanAndCount(sequence, endCopy, spanCondition, spanCount)
            }
            if (!remove && endModify != 0) {
                if (countMethod == CountMethod.WHOLE_SPAN) result.append(replacement)
                else repeat(spanCount!!.value) { result.append(replacement) }
            }
            if (endModify == length) break
            endCopy = unicodeSet.span(sequence, endModify, copySpan)
            result.append(sequence.subSequence(endModify, endCopy))
        }
        return result.toString()
    }
    open fun trim(sequence: CharSequence): CharSequence = trim(sequence, TrimOption.BOTH, SpanCondition.SIMPLE)
    open fun trim(sequence: CharSequence, trimOption: TrimOption): CharSequence = trim(sequence, trimOption, SpanCondition.SIMPLE)
    open fun trim(sequence: CharSequence, trimOption: TrimOption, spanCondition: SpanCondition): CharSequence {
        val length = sequence.length
        val endLead = if (trimOption != TrimOption.TRAILING) {
            val end = unicodeSet.span(sequence, spanCondition)
            if (end == length) return ""
            end
        } else 0
        val startTrail = if (trimOption != TrimOption.LEADING) unicodeSet.spanBack(sequence, spanCondition) else length
        return if (endLead == 0 && startTrail == length) sequence else sequence.subSequence(endLead, startTrail)
    }
}
