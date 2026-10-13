// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.OutputInt

/** Shared primitives for ICU string-aware spans; main span machines are being ported. */
internal class UnicodeSetStringSpan private constructor() {
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
