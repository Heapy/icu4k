// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet.SpanCondition
import io.github.heapy.icu4k.util.OutputInt

/** ICU frozen code-point membership tables over a shared immutable inversion list. */
internal class BMPSet private constructor(private val list: IntArray, private val listLength: Int, initialize: Boolean) {
    private var latin1Contains = BooleanArray(256)
    private var table7FF = IntArray(64)
    private var bmpBlockBits = IntArray(64)
    private var list4kStarts = IntArray(18)
    constructor(list: IntArray, length: Int) : this(list, length, true)
    constructor(other: BMPSet, list: IntArray, length: Int) : this(list, length, false) {
        latin1Contains = other.latin1Contains.copyOf(); table7FF = other.table7FF.copyOf()
        bmpBlockBits = other.bmpBlockBits.copyOf(); list4kStarts = other.list4kStarts.copyOf()
    }
    init {
        if (initialize) {
            list4kStarts[0] = findCodePoint(0x800, 0, listLength - 1)
            for (i in 1..16) list4kStarts[i] = findCodePoint(i shl 12, list4kStarts[i - 1], listLength - 1)
            list4kStarts[17] = listLength - 1
            initBits()
        }
    }
    fun contains(c: Int): Boolean = when {
        c <= 0xff -> latin1Contains[c]
        c <= 0x7ff -> table7FF[c and 63] and (1 shl (c shr 6)) != 0
        c < 0xd800 || c in 0xe000..0xffff -> containsBmp(c)
        c <= 0x10ffff -> containsSlow(c, list4kStarts[13], list4kStarts[17])
        else -> false
    }
    private fun containsBmp(c: Int): Boolean {
        val lead = c shr 12
        val bits = (bmpBlockBits[(c shr 6) and 63] shr lead) and 0x10001
        return if (bits <= 1) bits != 0 else containsSlow(c, list4kStarts[lead], list4kStarts[lead + 1])
    }
    fun span(s: CharSequence, start: Int, condition: SpanCondition, outCount: OutputInt?): Int {
        var i = start
        var supplementary = 0
        val contained = condition != SpanCondition.NOT_CONTAINED
        while (i < s.length) {
            val c = s[i].code
            var pair = false
            val member = when {
                c <= 0xff -> latin1Contains[c]
                c <= 0x7ff -> table7FF[c and 63] and (1 shl (c shr 6)) != 0
                c < 0xd800 || c >= 0xdc00 || i + 1 == s.length || s[i + 1].code !in 0xdc00..0xdfff -> containsBmp(c)
                else -> {
                    pair = true
                    val cp = ((c - 0xd800) shl 10) + s[i + 1].code - 0xdc00 + 0x10000
                    containsSlow(cp, list4kStarts[16], list4kStarts[17])
                }
            }
            if (member != contained) break
            if (pair) { ++supplementary; ++i }
            ++i
        }
        if (outCount != null) outCount.value = i - start - supplementary
        return i
    }
    fun spanBack(s: CharSequence, limit: Int, condition: SpanCondition): Int {
        var i = limit
        val contained = condition != SpanCondition.NOT_CONTAINED
        while (true) {
            val c = s[--i].code
            var pair = false
            val member = when {
                c <= 0xff -> latin1Contains[c]
                c <= 0x7ff -> table7FF[c and 63] and (1 shl (c shr 6)) != 0
                c < 0xd800 || c < 0xdc00 || i == 0 || s[i - 1].code !in 0xd800..0xdbff -> containsBmp(c)
                else -> {
                    pair = true
                    val cp = ((s[i - 1].code - 0xd800) shl 10) + c - 0xdc00 + 0x10000
                    containsSlow(cp, list4kStarts[16], list4kStarts[17])
                }
            }
            if (member != contained) break
            if (pair) --i
            if (i == 0) return 0
        }
        return i + 1
    }
    private fun initBits() {
        var index = 0
        var start: Int
        var limit: Int
        do {
            start = list[index++]
            limit = if (index < listLength) list[index++] else 0x110000
            if (start >= 256) break
            do { latin1Contains[start++] = true } while (start < limit && start < 256)
        } while (limit <= 256)
        while (start < 0x800) {
            set32x64Bits(table7FF, start, minOf(limit, 0x800))
            if (limit > 0x800) { start = 0x800; break }
            start = list[index++]; limit = if (index < listLength) list[index++] else 0x110000
        }
        var minStart = 0x800
        while (start < 0x10000) {
            if (limit > 0x10000) limit = 0x10000
            if (start < minStart) start = minStart
            if (start < limit) {
                if (start and 63 != 0) {
                    start = start shr 6
                    bmpBlockBits[start and 63] = bmpBlockBits[start and 63] or (0x10001 shl (start shr 6))
                    start = (start + 1) shl 6; minStart = start
                }
                if (start < limit) {
                    if (start < (limit and 63.inv())) set32x64Bits(bmpBlockBits, start shr 6, limit shr 6)
                    if (limit and 63 != 0) {
                        limit = limit shr 6
                        bmpBlockBits[limit and 63] = bmpBlockBits[limit and 63] or (0x10001 shl (limit shr 6))
                        limit = (limit + 1) shl 6; minStart = limit
                    }
                }
            }
            if (limit == 0x10000) break
            start = list[index++]; limit = if (index < listLength) list[index++] else 0x110000
        }
    }
    private fun findCodePoint(c: Int, low: Int, high: Int): Int {
        var lo = low; var hi = high
        if (c < list[lo]) return lo
        if (lo >= hi || c >= list[hi - 1]) return hi
        while (true) {
            val i = (lo + hi) ushr 1
            if (i == lo) break
            if (c < list[i]) hi = i else lo = i
        }
        return hi
    }
    private fun containsSlow(c: Int, low: Int, high: Int): Boolean = findCodePoint(c, low, high) and 1 != 0
    companion object {
        const val U16_SURROGATE_OFFSET = (0xd800 shl 10) + 0xdc00 - 0x10000
        private fun set32x64Bits(table: IntArray, start: Int, limit: Int) {
            var lead = start shr 6
            var trail = start and 63
            var bits = 1 shl lead
            if (start + 1 == limit) { table[trail] = table[trail] or bits; return }
            val limitLead = limit shr 6
            val limitTrail = limit and 63
            if (lead == limitLead) {
                while (trail < limitTrail) { table[trail] = table[trail] or bits; ++trail }
            } else {
                if (trail > 0) {
                    do { table[trail] = table[trail] or bits; ++trail } while (trail < 64)
                    ++lead
                }
                if (lead < limitLead) {
                    bits = ((1 shl lead) - 1).inv()
                    if (limitLead < 32) bits = bits and ((1 shl limitLead) - 1)
                    for (i in 0 until 64) table[i] = table[i] or bits
                }
                bits = 1 shl limitLead
                for (i in 0 until limitTrail) table[i] = table[i] or bits
            }
        }
    }
}
