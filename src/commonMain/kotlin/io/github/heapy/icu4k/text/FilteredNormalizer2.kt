// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/**
 * Normalizes SIMPLE spans in [filterSet], retaining all other text unchanged.
 * The normalizer and set are retained by reference; freeze the set before sharing.
 */
open class FilteredNormalizer2(private val norm2: Normalizer2, private val filterSet: UnicodeSet) : Normalizer2() {
    override fun normalize(src: CharSequence, dest: StringBuilder): StringBuilder {
        require(dest !== src)
        dest.setLength(0)
        normalize(src, dest, UnicodeSet.SpanCondition.SIMPLE)
        return dest
    }
    override fun normalize(src: CharSequence, dest: Appendable): Appendable {
        require(dest !== src)
        return normalize(src, dest, UnicodeSet.SpanCondition.SIMPLE)
    }
    override fun normalizeSecondAndAppend(first: StringBuilder, second: CharSequence): StringBuilder = normalizeSecondAndAppend(first, second, true)
    override fun append(first: StringBuilder, second: CharSequence): StringBuilder = normalizeSecondAndAppend(first, second, false)
    override fun getDecomposition(c: Int): String? = if (filterSet.contains(c)) norm2.getDecomposition(c) else null
    override fun getRawDecomposition(c: Int): String? = if (filterSet.contains(c)) norm2.getRawDecomposition(c) else null
    override fun composePair(a: Int, b: Int): Int = if (filterSet.contains(a) && filterSet.contains(b)) norm2.composePair(a, b) else -1
    override fun getCombiningClass(c: Int): Int = if (filterSet.contains(c)) norm2.getCombiningClass(c) else 0
    override fun hasBoundaryBefore(c: Int): Boolean = !filterSet.contains(c) || norm2.hasBoundaryBefore(c)
    override fun hasBoundaryAfter(c: Int): Boolean = !filterSet.contains(c) || norm2.hasBoundaryAfter(c)
    override fun isInert(c: Int): Boolean = !filterSet.contains(c) || norm2.isInert(c)

    override fun isNormalized(s: CharSequence): Boolean {
        var condition = UnicodeSet.SpanCondition.SIMPLE
        var prev = 0
        while (prev < s.length) {
            val limit = filterSet.span(s, prev, condition)
            if (condition == UnicodeSet.SpanCondition.NOT_CONTAINED) condition = UnicodeSet.SpanCondition.SIMPLE
            else {
                if (!norm2.isNormalized(s.subSequence(prev, limit))) return false
                condition = UnicodeSet.SpanCondition.NOT_CONTAINED
            }
            prev = limit
        }
        return true
    }
    override fun quickCheck(s: CharSequence): Normalizer.QuickCheckResult {
        var result = Normalizer.YES
        var condition = UnicodeSet.SpanCondition.SIMPLE
        var prev = 0
        while (prev < s.length) {
            val limit = filterSet.span(s, prev, condition)
            if (condition == UnicodeSet.SpanCondition.NOT_CONTAINED) condition = UnicodeSet.SpanCondition.SIMPLE
            else {
                val qc = norm2.quickCheck(s.subSequence(prev, limit))
                if (qc === Normalizer.NO) return qc
                if (qc === Normalizer.MAYBE) result = qc
                condition = UnicodeSet.SpanCondition.NOT_CONTAINED
            }
            prev = limit
        }
        return result
    }
    override fun spanQuickCheckYes(s: CharSequence): Int {
        var condition = UnicodeSet.SpanCondition.SIMPLE
        var prev = 0
        while (prev < s.length) {
            val limit = filterSet.span(s, prev, condition)
            if (condition == UnicodeSet.SpanCondition.NOT_CONTAINED) condition = UnicodeSet.SpanCondition.SIMPLE
            else {
                val yesLimit = prev + norm2.spanQuickCheckYes(s.subSequence(prev, limit))
                if (yesLimit < limit) return yesLimit
                condition = UnicodeSet.SpanCondition.NOT_CONTAINED
            }
            prev = limit
        }
        return s.length
    }
    private fun normalize(src: CharSequence, dest: Appendable, initialCondition: UnicodeSet.SpanCondition): Appendable {
        val temp = StringBuilder()
        var condition = initialCondition
        var prev = 0
        while (prev < src.length) {
            val limit = filterSet.span(src, prev, condition)
            if (condition == UnicodeSet.SpanCondition.NOT_CONTAINED) {
                if (limit != prev) dest.append(src, prev, limit)
                condition = UnicodeSet.SpanCondition.SIMPLE
            } else {
                if (limit != prev) dest.append(norm2.normalize(src.subSequence(prev, limit), temp))
                condition = UnicodeSet.SpanCondition.NOT_CONTAINED
            }
            prev = limit
        }
        return dest
    }
    private fun normalizeSecondAndAppend(first: StringBuilder, second: CharSequence, doNormalize: Boolean): StringBuilder {
        require(first !== second)
        if (first.isEmpty()) return if (doNormalize) normalize(second, first) else first.append(second)
        val prefixLimit = filterSet.span(second, 0, UnicodeSet.SpanCondition.SIMPLE)
        if (prefixLimit != 0) {
            val prefix = second.subSequence(0, prefixLimit)
            val suffixStart = filterSet.spanBack(first, Int.MAX_VALUE, UnicodeSet.SpanCondition.SIMPLE)
            if (suffixStart == 0) {
                if (doNormalize) norm2.normalizeSecondAndAppend(first, prefix) else norm2.append(first, prefix)
            } else {
                val middle = StringBuilder(first.subSequence(suffixStart, first.length))
                if (doNormalize) norm2.normalizeSecondAndAppend(middle, prefix) else norm2.append(middle, prefix)
                first.setLength(suffixStart)
                first.append(middle)
            }
        }
        if (prefixLimit < second.length) {
            val rest = second.subSequence(prefixLimit, second.length)
            if (doNormalize) normalize(rest, first, UnicodeSet.SpanCondition.NOT_CONTAINED) else first.append(rest)
        }
        return first
    }
}
