// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.text.UTF16

/** Map from Unicode code points to integers, with same-value range enumeration. */
abstract class CodePointMap protected constructor() : Iterable<CodePointMap.Range> {
    enum class RangeOption { NORMAL, FIXED_LEAD_SURROGATES, FIXED_ALL_SURROGATES }
    fun interface ValueFilter { fun apply(value: Int): Int }

    /** Mutable result reused by range iterators. Both endpoints are inclusive. */
    class Range {
        private var rangeStart = -1
        private var rangeEnd = -1
        private var rangeValue = 0
        fun getStart(): Int = rangeStart
        fun getEnd(): Int = rangeEnd
        fun getValue(): Int = rangeValue
        fun set(start: Int, end: Int, value: Int) {
            rangeStart = start
            rangeEnd = end
            rangeValue = value
        }
    }

    abstract operator fun get(c: Int): Int
    abstract fun getRange(start: Int, filter: ValueFilter?, range: Range): Boolean

    open fun getRange(start: Int, option: RangeOption, surrogateValue: Int, filter: ValueFilter?, range: Range): Boolean {
        if (!getRange(start, filter, range)) return false
        if (option == RangeOption.NORMAL) return true
        val surrogateEnd = if (option == RangeOption.FIXED_ALL_SURROGATES) 0xdfff else 0xdbff
        val end = range.getEnd()
        if (end < 0xd7ff || start > surrogateEnd) return true
        if (range.getValue() == surrogateValue) {
            if (end >= surrogateEnd) return true
        } else {
            if (start <= 0xd7ff) {
                range.set(start, 0xd7ff, range.getValue())
                return true
            }
            range.set(start, end, surrogateValue)
            if (end > surrogateEnd) {
                range.set(start, surrogateEnd, surrogateValue)
                return true
            }
        }
        if (getRange(surrogateEnd + 1, filter, range) && range.getValue() == surrogateValue) {
            range.set(start, range.getEnd(), range.getValue())
            return true
        }
        range.set(start, surrogateEnd, surrogateValue)
        return true
    }

    /** Reuses one Range object; changing its end changes where iteration resumes. */
    override fun iterator(): Iterator<Range> = object : Iterator<Range> {
        private val range = Range()
        override fun hasNext(): Boolean = range.getEnd() in -1 until 0x10ffff
        override fun next(): Range {
            if (!getRange(range.getEnd() + 1, null, range)) throw NoSuchElementException()
            return range
        }
    }

    open inner class StringIterator internal constructor(protected var s: CharSequence, protected var sIndex: Int) {
        protected var c = -1
        protected var mappedValue = 0

        fun reset(s: CharSequence, sIndex: Int) {
            this.s = s
            this.sIndex = sIndex
            c = -1
            mappedValue = 0
        }

        open fun next(): Boolean {
            if (sIndex >= s.length) return false
            val first = s[sIndex++].code
            c = first
            if (UTF16.isLeadSurrogate(first) && sIndex < s.length && UTF16.isTrailSurrogate(s[sIndex].code)) {
                c = ((first - 0xd800) shl 10) + s[sIndex++].code - 0xdc00 + 0x10000
            }
            mappedValue = get(c)
            return true
        }

        open fun previous(): Boolean {
            if (sIndex <= 0) return false
            val last = s[--sIndex].code
            c = last
            if (UTF16.isTrailSurrogate(last) && sIndex > 0 && UTF16.isLeadSurrogate(s[sIndex - 1].code)) {
                c = ((s[--sIndex].code - 0xd800) shl 10) + last - 0xdc00 + 0x10000
            }
            mappedValue = get(c)
            return true
        }

        fun getIndex(): Int = sIndex
        fun getCodePoint(): Int = c
        fun getValue(): Int = mappedValue
    }

    open fun stringIterator(s: CharSequence, sIndex: Int): StringIterator = StringIterator(s, sIndex)
}
