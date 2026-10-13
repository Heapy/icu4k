// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.OutputInt

/** Shared primitives for ICU string-aware spans; main span machines are being ported. */
internal class UnicodeSetStringSpan private constructor(
    private val spanSet: UnicodeSet,
    private val strings: ArrayList<String>,
    private val all: Boolean,
) {
    private var spanNotSet: UnicodeSet? = null
    private var spanLengths: ShortArray? = null
    private var maxLength16 = 0
    private var someRelevant = false

    constructor(set: UnicodeSet, setStrings: ArrayList<String>, which: Int) :
        this(UnicodeSet(0, UnicodeSet.MAX_VALUE).retainAll(set), setStrings, which == ALL) {
        if (which and NOT_CONTAINED != 0) spanNotSet = spanSet
        var i = 0
        while (i < strings.size) {
            val string = strings[i]
            if (string.isEmpty()) { strings.removeAt(i); continue }
            val length = spanForward(string)
            if (length < string.length) someRelevant = true
            if (string.length > maxLength16) maxLength16 = string.length
            ++i
        }
        if (someRelevant || which and WITH_COUNT != 0) {
            if (all) spanSet.freezeCodePointSet()
            val backOffset = if (all) strings.size else 0
            val lengths = ShortArray(if (all) strings.size * 2 else strings.size)
            spanLengths = lengths
            for (index in strings.indices) {
                val string = strings[index]
                val length = spanForward(string)
                if (length < string.length) {
                    if (which and CONTAINED != 0) {
                        if (which and FWD != 0) lengths[index] = makeSpanLengthByte(length)
                        if (which and BACK != 0) lengths[backOffset + index] = makeSpanLengthByte(string.length - spanSet.spanCodePointsBack(string, string.length, UnicodeSet.SpanCondition.CONTAINED))
                    } else { lengths[index] = 0; lengths[backOffset + index] = 0 }
                    if (which and NOT_CONTAINED != 0) {
                        if (which and FWD != 0) addToSpanNotSet(codePointAt(string, 0))
                        if (which and BACK != 0) addToSpanNotSet(codePointBefore(string, string.length))
                    }
                } else {
                    lengths[index] = ALL_CP_CONTAINED
                    if (all) lengths[backOffset + index] = ALL_CP_CONTAINED
                }
            }
            if (all) spanNotSet!!.freezeCodePointSet()
        }
    }
    constructor(other: UnicodeSetStringSpan, newStrings: ArrayList<String>) : this(other.spanSet, newStrings, true) {
        maxLength16 = other.maxLength16; someRelevant = other.someRelevant
        spanNotSet = if (other.spanNotSet === other.spanSet) spanSet else other.spanNotSet!!.clone()
        spanLengths = other.spanLengths!!.copyOf()
    }
    fun needsStringSpanUTF16(): Boolean = someRelevant
    fun contains(c: Int): Boolean = spanSet.contains(c)
    private fun spanForward(string: String): Int = spanSet.spanCodePointsAndCount(string, 0, UnicodeSet.SpanCondition.CONTAINED, null)
    private fun addToSpanNotSet(c: Int) {
        if (spanNotSet == null || spanNotSet === spanSet) {
            if (spanSet.contains(c)) return
            spanNotSet = spanSet.cloneAsThawed()
        }
        spanNotSet!!.add(c)
    }

    fun spanBack(s: CharSequence, length: Int, condition: UnicodeSet.SpanCondition): Int {
        if (condition == UnicodeSet.SpanCondition.NOT_CONTAINED) return spanNotBack(s, length)
        var pos = if (length == 0) 0 else spanSet.spanCodePointsBack(s, length, UnicodeSet.SpanCondition.CONTAINED)
        if (pos == 0) return 0
        var spanLength = length - pos
        val offsets = OffsetList()
        offsets.setMaxLength(if (condition == UnicodeSet.SpanCondition.CONTAINED) maxLength16 else 0)
        val backOffset = if (all) strings.size else 0
        val lengths = spanLengths!!
        while (true) {
            if (condition == UnicodeSet.SpanCondition.CONTAINED) {
                for (i in strings.indices) {
                    var overlap = lengths[backOffset + i].toInt()
                    if (overlap == ALL_CP_CONTAINED.toInt()) continue
                    val string = strings[i]
                    val length16 = string.length
                    if (overlap >= LONG_SPAN) overlap = length16 - if (codePointAt(string, 0) > 0xffff) 2 else 1
                    if (overlap > spanLength) overlap = spanLength
                    var dec = length16 - overlap
                    while (true) {
                        if (dec > pos) break
                        if (!offsets.containsOffset(dec) && matches16CPB(s, pos - dec, length, string, length16)) {
                            if (dec == pos) return 0
                            offsets.addOffset(dec)
                        }
                        if (overlap == 0) break
                        --overlap; ++dec
                    }
                }
            } else {
                var maxDec = 0
                var maxOverlap = 0
                for (i in strings.indices) {
                    var overlap = lengths[backOffset + i].toInt()
                    val string = strings[i]
                    val length16 = string.length
                    if (overlap >= LONG_SPAN) overlap = length16
                    if (overlap > spanLength) overlap = spanLength
                    var dec = length16 - overlap
                    while (true) {
                        if (dec > pos || overlap < maxOverlap) break
                        if ((overlap > maxOverlap || dec > maxDec) && matches16CPB(s, pos - dec, length, string, length16)) {
                            maxDec = dec; maxOverlap = overlap
                            break
                        }
                        --overlap; ++dec
                    }
                }
                if (maxDec != 0 || maxOverlap != 0) {
                    pos -= maxDec
                    if (pos == 0) return 0
                    spanLength = 0
                    continue
                }
            }
            if (spanLength != 0 || pos == length) {
                if (offsets.isEmpty()) return pos
            } else {
                if (offsets.isEmpty()) {
                    val oldPos = pos
                    pos = spanSet.spanCodePointsBack(s, oldPos, UnicodeSet.SpanCondition.CONTAINED)
                    spanLength = oldPos - pos
                    if (pos == 0 || spanLength == 0) return pos
                    continue
                } else {
                    spanLength = spanOneBack(spanSet, s, pos)
                    if (spanLength > 0) {
                        if (spanLength == pos) return 0
                        pos -= spanLength
                        offsets.shift(spanLength)
                        spanLength = 0
                        continue
                    }
                }
            }
            pos -= offsets.popMinimum(null)
            spanLength = 0
        }
    }

    fun span(s: CharSequence, start: Int, condition: UnicodeSet.SpanCondition): Int {
        if (condition == UnicodeSet.SpanCondition.NOT_CONTAINED) return spanNot(s, start, null)
        val limit = if (start >= s.length) s.length else spanSet.spanCodePointsAndCount(s, start, UnicodeSet.SpanCondition.CONTAINED, null)
        if (limit == s.length) return limit
        return spanWithStrings(s, start, limit, condition)
    }
    private fun spanWithStrings(s: CharSequence, start: Int, initialLimit: Int, condition: UnicodeSet.SpanCondition): Int {
        val offsets = OffsetList()
        offsets.setMaxLength(if (condition == UnicodeSet.SpanCondition.CONTAINED) maxLength16 else 0)
        val length = s.length
        var spanLimit = initialLimit
        var pos = spanLimit
        var rest = length - spanLimit
        var spanLength = spanLimit - start
        val lengths = spanLengths!!
        while (true) {
            if (condition == UnicodeSet.SpanCondition.CONTAINED) {
                for (i in strings.indices) {
                    var overlap = lengths[i].toInt()
                    if (overlap == ALL_CP_CONTAINED.toInt()) continue
                    val string = strings[i]
                    val length16 = string.length
                    if (overlap >= LONG_SPAN) overlap = length16 - if (codePointBefore(string, length16) > 0xffff) 2 else 1
                    if (overlap > spanLength) overlap = spanLength
                    var inc = length16 - overlap
                    while (true) {
                        if (inc > rest) break
                        if (!offsets.containsOffset(inc) && matches16CPB(s, pos - overlap, length, string, length16)) {
                            if (inc == rest) return length
                            offsets.addOffset(inc)
                        }
                        if (overlap == 0) break
                        --overlap; ++inc
                    }
                }
            } else {
                var maxInc = 0; var maxOverlap = 0
                for (i in strings.indices) {
                    var overlap = lengths[i].toInt()
                    val string = strings[i]
                    val length16 = string.length
                    if (overlap >= LONG_SPAN) overlap = length16
                    if (overlap > spanLength) overlap = spanLength
                    var inc = length16 - overlap
                    while (true) {
                        if (inc > rest || overlap < maxOverlap) break
                        if ((overlap > maxOverlap || inc > maxInc) && matches16CPB(s, pos - overlap, length, string, length16)) {
                            maxInc = inc; maxOverlap = overlap; break
                        }
                        --overlap; ++inc
                    }
                }
                if (maxInc != 0 || maxOverlap != 0) {
                    pos += maxInc; rest -= maxInc
                    if (rest == 0) return length
                    spanLength = 0
                    continue
                }
            }
            if (spanLength != 0 || pos == 0) {
                if (offsets.isEmpty()) return pos
            } else {
                if (offsets.isEmpty()) {
                    spanLimit = spanSet.spanCodePointsAndCount(s, pos, UnicodeSet.SpanCondition.CONTAINED, null)
                    spanLength = spanLimit - pos
                    if (spanLength == rest || spanLength == 0) return spanLimit
                    pos += spanLength; rest -= spanLength
                    continue
                } else {
                    spanLength = spanOne(spanSet, s, pos, rest)
                    if (spanLength > 0) {
                        if (spanLength == rest) return length
                        pos += spanLength; rest -= spanLength
                        offsets.shift(spanLength); spanLength = 0
                        continue
                    }
                }
            }
            val minOffset = offsets.popMinimum(null)
            pos += minOffset; rest -= minOffset; spanLength = 0
        }
    }
    fun spanAndCount(s: CharSequence, start: Int, condition: UnicodeSet.SpanCondition, outCount: OutputInt): Int {
        if (condition == UnicodeSet.SpanCondition.NOT_CONTAINED) return spanNot(s, start, outCount)
        if (condition == UnicodeSet.SpanCondition.CONTAINED) return spanContainedAndCount(s, start, outCount)
        var pos = start
        var rest = s.length - start
        var count = 0
        while (rest != 0) {
            val cpLength = spanOne(spanSet, s, pos, rest)
            var maxInc = if (cpLength > 0) cpLength else 0
            for (i in strings.indices) {
                val string = strings[i]
                if (maxInc < string.length && string.length <= rest && matches16CPB(s, pos, s.length, string, string.length))
                    maxInc = string.length
            }
            if (maxInc == 0) { outCount.value = count; return pos }
            ++count; pos += maxInc; rest -= maxInc
        }
        outCount.value = count
        return pos
    }
    private fun spanContainedAndCount(s: CharSequence, start: Int, outCount: OutputInt): Int {
        // Per-call scratch retains the original ring algorithm without shared mutable state.
        val offsets = OffsetList()
        offsets.setMaxLength(maxLength16)
        var pos = start
        var rest = s.length - start
        var count = 0
        while (rest != 0) {
            val cpLength = spanOne(spanSet, s, pos, rest)
            if (cpLength > 0) offsets.addOffsetAndCount(cpLength, count + 1)
            for (i in strings.indices) {
                val string = strings[i]
                val length = string.length
                if (length <= rest && !offsets.hasCountAtOffset(length, count + 1) && matches16CPB(s, pos, s.length, string, length))
                    offsets.addOffsetAndCount(length, count + 1)
            }
            if (offsets.isEmpty()) { outCount.value = count; return pos }
            val minOffset = offsets.popMinimum(outCount)
            count = outCount.value; pos += minOffset; rest -= minOffset
        }
        outCount.value = count
        return pos
    }
    internal fun spanNot(s: CharSequence, start: Int, outCount: OutputInt?): Int {
        val length = s.length
        var pos = start
        var rest = length - start
        var count = 0
        do {
            if (pos >= length) return length
            val limit = spanNotSet!!.spanCodePointsAndCount(s, pos, UnicodeSet.SpanCondition.NOT_CONTAINED, outCount)
            if (outCount != null) { count += outCount.value; outCount.value = count }
            if (limit == length) return length
            pos = limit; rest = length - limit
            val cpLength = spanOne(spanSet, s, pos, rest)
            if (cpLength > 0) return pos
            for (i in strings.indices) {
                if (spanLengths!![i] == ALL_CP_CONTAINED) continue
                val string = strings[i]
                if (string.length <= rest && matches16CPB(s, pos, length, string, string.length)) return pos
            }
            pos -= cpLength; rest += cpLength; ++count
        } while (rest != 0)
        if (outCount != null) outCount.value = count
        return length
    }
    internal fun spanNotBack(s: CharSequence, length: Int): Int {
        var pos = length
        do {
            if (pos == 0) return 0
            pos = spanNotSet!!.spanCodePointsBack(s, pos, UnicodeSet.SpanCondition.NOT_CONTAINED)
            if (pos == 0) return 0
            val cpLength = spanOneBack(spanSet, s, pos)
            if (cpLength > 0) return pos
            for (i in strings.indices) {
                if (spanLengths!![i] == ALL_CP_CONTAINED) continue
                val string = strings[i]
                if (string.length <= pos && matches16CPB(s, pos - string.length, length, string, string.length)) return pos
            }
            pos += cpLength
        } while (pos != 0)
        return 0
    }
    class OffsetList {
        private var list = IntArray(16)
        private var length = 0
        private var start = 0
        fun setMaxLength(maxLength: Int) { if (maxLength > list.size) list = IntArray(maxLength); clear() }
        fun clear() { list.fill(0); start = 0; length = 0 }
        fun isEmpty(): Boolean = length == 0
        fun shift(delta: Int) {
            var i = start + delta
            if (i >= list.size) i -= list.size
            if (list[i] != 0) { list[i] = 0; --length }
            start = i
        }
        fun addOffset(offset: Int) {
            var i = start + offset
            if (i >= list.size) i -= list.size
            list[i] = 1; ++length
        }
        fun addOffsetAndCount(offset: Int, count: Int) {
            var i = start + offset
            if (i >= list.size) i -= list.size
            if (list[i] == 0) { list[i] = count; ++length }
            else if (count < list[i]) list[i] = count
        }
        fun containsOffset(offset: Int): Boolean {
            var i = start + offset
            if (i >= list.size) i -= list.size
            return list[i] != 0
        }
        fun hasCountAtOffset(offset: Int, count: Int): Boolean {
            var i = start + offset
            if (i >= list.size) i -= list.size
            return list[i] != 0 && list[i] <= count
        }
        fun popMinimum(outCount: OutputInt?): Int {
            var i = start
            while (++i < list.size) {
                val count = list[i]
                if (count != 0) {
                    list[i] = 0; --length
                    val result = i - start; start = i
                    if (outCount != null) outCount.value = count
                    return result
                }
            }
            val result = list.size - start
            i = 0
            while (list[i] == 0) ++i
            val count = list[i]
            list[i] = 0; --length; start = i
            if (outCount != null) outCount.value = count
            return result + i
        }
    }
    companion object {
        const val WITH_COUNT = 0x40
        const val FWD = 0x20
        const val BACK = 0x10
        const val CONTAINED = 2
        const val NOT_CONTAINED = 1
        const val ALL = 0x7f
        const val FWD_UTF16_CONTAINED = FWD or CONTAINED
        const val FWD_UTF16_NOT_CONTAINED = FWD or NOT_CONTAINED
        const val BACK_UTF16_CONTAINED = BACK or CONTAINED
        const val BACK_UTF16_NOT_CONTAINED = BACK or NOT_CONTAINED
        private fun codePointAt(s: String, index: Int): Int {
            val lead = s[index].code
            if (lead in 0xd800..0xdbff && index + 1 < s.length && s[index + 1].code in 0xdc00..0xdfff)
                return ((lead - 0xd800) shl 10) + s[index + 1].code - 0xdc00 + 0x10000
            return lead
        }
        private fun codePointBefore(s: String, index: Int): Int {
            val trail = s[index - 1].code
            if (trail in 0xdc00..0xdfff && index >= 2 && s[index - 2].code in 0xd800..0xdbff)
                return ((s[index - 2].code - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
            return trail
        }
        const val ALL_CP_CONTAINED: Short = 255
        const val LONG_SPAN: Short = 254
        fun makeSpanLengthByte(length: Int): Short = if (length < LONG_SPAN) length.toShort() else LONG_SPAN
        fun matches16CPB(s: CharSequence, start: Int, limit: Int, t: String, length: Int): Boolean {
            var remaining = length
            var end = start + length
            while (remaining-- > 0) if (s[--end] != t[remaining]) return false
            return !(start > 0 && s[start - 1].code in 0xd800..0xdbff && s[start].code in 0xdc00..0xdfff) &&
                !(start + length < limit && s[start + length - 1].code in 0xd800..0xdbff && s[start + length].code in 0xdc00..0xdfff)
        }
        fun spanOne(set: UnicodeSet, s: CharSequence, start: Int, length: Int): Int {
            val c = s[start].code
            if (c in 0xd800..0xdbff && length >= 2) {
                val trail = s[start + 1].code
                if (trail in 0xdc00..0xdfff) {
                    val cp = ((c - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
                    return if (set.contains(cp)) 2 else -2
                }
            }
            return if (set.contains(c)) 1 else -1
        }
        fun spanOneBack(set: UnicodeSet, s: CharSequence, length: Int): Int {
            val c = s[length - 1].code
            if (c in 0xdc00..0xdfff && length >= 2) {
                val lead = s[length - 2].code
                if (lead in 0xd800..0xdbff) {
                    val cp = ((lead - 0xd800) shl 10) + c - 0xdc00 + 0x10000
                    return if (set.contains(cp)) 2 else -2
                }
            }
            return if (set.contains(c)) 1 else -1
        }
    }
}
