// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2015, Google, Inc., International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.CharSequences
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.ICUException

/** ICU rectangular code-point string ranges and compaction. */
internal object StringRange {
    fun interface Adder { fun add(start: String?, end: String?) }
    val COMPARE_INT_ARRAYS: Comparator<IntArray> = Comparator { a, b ->
        var diff = 0
        for (i in 0 until minOf(a.size, b.size)) {
            diff = a[i] - b[i]
            if (diff != 0) break
        }
        if (diff != 0) diff else a.size - b.size
    }
    fun compact(source: Set<String>, adder: Adder, shorterPairs: Boolean, moreCompact: Boolean = false) {
        if (!moreCompact) {
            var start: String? = null
            var end: String? = null
            var lastCp = 0
            var prefixLength = 0
            for (s in source) {
                if (start != null) {
                    if (s.regionMatches(0, start, 0, prefixLength)) {
                        val cp = codePointAt(s, prefixLength)
                        if (cp == lastCp + 1 && s.length == prefixLength + UTF16.getCharCount(cp)) {
                            end = s; lastCp = cp
                            continue
                        }
                    }
                    adder.add(start, if (end == null) null else if (!shorterPairs) end else end.substring(prefixLength))
                }
                start = s; end = null
                if (s.isEmpty()) throw IndexOutOfBoundsException()
                var cp = s.last().code
                if (cp in 0xdc00..0xdfff && s.length > 1 && s[s.length - 2].code in 0xd800..0xdbff) {
                    cp = 0x10000 + ((s[s.length - 2].code - 0xd800) shl 10) + cp - 0xdc00
                }
                lastCp = cp; prefixLength = s.length - UTF16.getCharCount(cp)
            }
            adder.add(start, if (end == null) null else if (!shorterPairs) end else end.substring(prefixLength))
        } else {
            val byLength = source.map { Ranges(it) }.groupBy { it.ranges.size }
            for (size in byLength.keys.sorted()) {
                val items = byLength.getValue(size).sorted().toMutableList()
                // The upstream TreeSet uses comparison, not identity, for uniqueness.
                var index = 1
                while (index < items.size) {
                    if (items[index - 1].compareTo(items[index]) == 0) items.removeAt(index) else index++
                }
                for (pivot in size - 1 downTo 0) {
                    var last: Ranges? = null
                    val iterator = items.iterator()
                    while (iterator.hasNext()) {
                        val item = iterator.next()
                        if (last == null) last = item
                        else if (last.merge(pivot, item)) iterator.remove()
                        else last = item
                    }
                }
                for (ranges in items) adder.add(ranges.start(), ranges.end(shorterPairs))
            }
        }
    }
    private fun codePointAt(s: String, index: Int): Int {
        if (index !in s.indices) throw IndexOutOfBoundsException()
        val lead = s[index].code
        if (lead in 0xd800..0xdbff && index + 1 < s.length && s[index + 1].code in 0xdc00..0xdfff) {
            return 0x10000 + ((lead - 0xd800) shl 10) + s[index + 1].code - 0xdc00
        }
        return lead
    }
    private class Range(val min: Int, var max: Int) : Comparable<Range> {
        override fun compareTo(other: Range): Int = if (min != other.min) min - other.min else max - other.max
        override fun equals(other: Any?): Boolean = other is Range && compareTo(other) == 0
        override fun hashCode(): Int = min * 37 + max
    }
    private class Ranges(s: String) : Comparable<Ranges> {
        val ranges = CharSequences.codePoints(s).map { Range(it, it) }
        fun merge(pivot: Int, other: Ranges): Boolean {
            for (i in ranges.size - 1 downTo 0) {
                if (i == pivot) { if (ranges[i].max != other.ranges[i].min - 1) return false }
                else if (ranges[i] != other.ranges[i]) return false
            }
            ranges[pivot].max = other.ranges[pivot].max
            return true
        }
        fun start(): String = buildString { for (range in ranges) UTF16.appendCodePoint(this, range.min) }
        fun end(shorter: Boolean): String? {
            val first = ranges.indexOfFirst { it.min != it.max }
            if (first < 0) return null
            return buildString { for (i in (if (shorter) first else 0) until ranges.size) UTF16.appendCodePoint(this, ranges[i].max) }
        }
        override fun compareTo(other: Ranges): Int {
            if (ranges.size != other.ranges.size) return ranges.size - other.ranges.size
            for (i in ranges.indices) { val diff = ranges[i].compareTo(other.ranges[i]); if (diff != 0) return diff }
            return 0
        }
    }
    fun <C : MutableCollection<String>> expand(start: String?, end: String?, requireSameLength: Boolean, output: C): C {
        if (start == null || end == null) throw ICUException("Range must have 2 valid strings")
        val starts = CharSequences.codePoints(start)
        val ends = CharSequences.codePoints(end)
        val offset = starts.size - ends.size
        if (requireSameLength && offset != 0) throw ICUException("Range must have equal-length strings")
        else if (offset < 0) throw ICUException("Range must have start-length ≥ end-length")
        else if (ends.isEmpty()) throw ICUException("Range must have end-length > 0")
        val builder = StringBuilder()
        for (i in 0 until offset) UTF16.appendCodePoint(builder, starts[i])
        add(0, offset, starts, ends, builder, output)
        return output
    }
    private fun add(index: Int, offset: Int, starts: IntArray, ends: IntArray, builder: StringBuilder, output: MutableCollection<String>) {
        val start = starts[index + offset]
        val end = ends[index]
        if (start > end) throw ICUException("Range must have xᵢ ≤ yᵢ for each index i")
        val last = index == ends.size - 1
        val length = builder.length
        for (cp in start..end) {
            UTF16.appendCodePoint(builder, cp)
            if (last) output.add(builder.toString()) else add(index + 1, offset, starts, ends, builder, output)
            builder.setLength(length)
        }
    }
}
