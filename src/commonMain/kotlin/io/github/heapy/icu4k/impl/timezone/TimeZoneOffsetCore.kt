// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2011-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.Output

/** Numeric ISO offset engine used by TimeZoneFormat, including its permissive prefix parser. */
internal object TimeZoneOffsetCore {
    enum class OffsetFields { H, HM, HMS }
    private const val HOUR = 3_600_000
    private const val MINUTE = 60_000
    private const val SECOND = 1_000

    fun formatOffsetISO8601Basic(offset: Int, useUtcIndicator: Boolean, isShort: Boolean, ignoreSeconds: Boolean): String =
        formatOffsetISO8601(offset,true,useUtcIndicator,isShort,ignoreSeconds)
    fun formatOffsetISO8601Extended(offset: Int, useUtcIndicator: Boolean, isShort: Boolean, ignoreSeconds: Boolean): String =
        formatOffsetISO8601(offset,false,useUtcIndicator,isShort,ignoreSeconds)
    fun formatOffsetISO8601(offset: Int, isBasic: Boolean, useUtcIndicator: Boolean, isShort: Boolean, ignoreSeconds: Boolean): String {
        var absolute = if (offset < 0) -offset else offset
        // Deliberately retain Int.MIN_VALUE overflow and the source's UTC-before-validation order.
        if (useUtcIndicator && (absolute < SECOND || (ignoreSeconds && absolute < MINUTE))) return "Z"
        val minFields = if (isShort) OffsetFields.H else OffsetFields.HM
        val maxFields = if (ignoreSeconds) OffsetFields.HM else OffsetFields.HMS
        if (absolute >= 24 * HOUR) throw IllegalArgumentException("Offset out of range :$offset")
        val fields = IntArray(3)
        fields[0] = absolute / HOUR
        absolute %= HOUR
        fields[1] = absolute / MINUTE
        absolute %= MINUTE
        fields[2] = absolute / SECOND
        if (timeZoneOffsetAssertionsEnabled()) {
            if (fields[0] !in 0..23) throw AssertionError()
            if (fields[1] !in 0..59) throw AssertionError()
            if (fields[2] !in 0..59) throw AssertionError()
        }
        var last = maxFields.ordinal
        while (last > minFields.ordinal && fields[last] == 0) last--
        val sign = if (offset < 0 && (0..last).any { fields[it] != 0 }) '-' else '+'
        return buildString {
            append(sign)
            for (i in 0..last) {
                if (!isBasic && i != 0) append(':')
                if (fields[i] < 10) append('0')
                append(fields[i])
            }
        }
    }

    fun parseOffsetISO8601(text: String?, pos: ParsePosition?, extendedOnly: Boolean = false, hasDigitOffset: Output<Boolean>? = null): Int {
        if (hasDigitOffset != null) hasDigitOffset.value = false
        val start = pos!!.index
        val input = text!!
        if (start >= input.length) { pos.errorIndex = start; return 0 }
        val first = charAt(input,start)
        if (first == 'Z' || first == 'z') { pos.index = start + 1; return 0 }
        val sign = when(first) { '+' -> 1; '-' -> -1; else -> { pos.errorIndex = start; return 0 } }
        val next = ParsePosition(start + 1)
        var offset = parseAsciiOffsetFields(input,next,':',OffsetFields.H,OffsetFields.HMS)
        if (next.errorIndex == -1 && !extendedOnly && next.index - start <= 3) {
            val basic = ParsePosition(start + 1)
            val value = parseAbuttingAsciiOffsetFields(input,basic,OffsetFields.H,OffsetFields.HMS,false)
            if (basic.errorIndex == -1 && basic.index > next.index) { offset = value; next.index = basic.index }
        }
        if (next.errorIndex != -1) { pos.errorIndex = start; return 0 }
        pos.index = next.index
        if (hasDigitOffset != null) hasDigitOffset.value = true
        return sign * offset
    }

    fun parseAbuttingAsciiOffsetFields(text: String, pos: ParsePosition, minFields: OffsetFields, maxFields: OffsetFields, fixedHourWidth: Boolean): Int {
        val start = pos.index
        val minDigits = 2 * (minFields.ordinal + 1) - if (fixedHourWidth) 0 else 1
        val digits = IntArray(2 * (maxFields.ordinal + 1))
        var count = 0
        var index = start
        while (count < digits.size && index < text.length) {
            val digit = "0123456789".indexOf(charAt(text,index))
            if (digit < 0) break
            digits[count++] = digit
            index++
        }
        if (fixedHourWidth && count and 1 != 0) count--
        if (count < minDigits) { pos.errorIndex = start; return 0 }
        var hour = 0
        var minute = 0
        var second = 0
        var parsed = false
        while (count >= minDigits) {
            when(count) {
                1 -> hour = digits[0]
                2 -> hour = digits[0] * 10 + digits[1]
                3 -> { hour = digits[0]; minute = digits[1] * 10 + digits[2] }
                4 -> { hour = digits[0] * 10 + digits[1]; minute = digits[2] * 10 + digits[3] }
                5 -> { hour = digits[0]; minute = digits[1] * 10 + digits[2]; second = digits[3] * 10 + digits[4] }
                6 -> { hour = digits[0] * 10 + digits[1]; minute = digits[2] * 10 + digits[3]; second = digits[4] * 10 + digits[5] }
            }
            if (hour <= 23 && minute <= 59 && second <= 59) { parsed = true; break }
            count -= if (fixedHourWidth) 2 else 1
            hour = 0; minute = 0; second = 0
        }
        if (!parsed) { pos.errorIndex = start; return 0 }
        pos.index = start + count
        return ((hour * 60 + minute) * 60 + second) * SECOND
    }

    fun parseAsciiOffsetFields(text: String, pos: ParsePosition, separator: Char, minFields: OffsetFields, maxFields: OffsetFields): Int {
        val start = pos.index
        val values = intArrayOf(0,0,0)
        val lengths = intArrayOf(0,-1,-1)
        var index = start
        var field = 0
        while (index < text.length && field <= maxFields.ordinal) {
            val c = charAt(text,index)
            if (c == separator) {
                if (field == 0) {
                    if (lengths[0] == 0) break
                    field++
                } else {
                    if (lengths[field] != -1) break
                    lengths[field] = 0
                }
                index++
                continue
            } else if (lengths[field] == -1) break
            val digit = "0123456789".indexOf(c)
            if (digit < 0) break
            values[field] = values[field] * 10 + digit
            lengths[field]++
            if (lengths[field] >= 2) field++
            index++
        }
        var offset = 0
        var length = 0
        var parsed: OffsetFields? = null
        do {
            if (lengths[0] == 0) break
            if (values[0] > 23) {
                offset = values[0] / 10 * HOUR
                parsed = OffsetFields.H
                length = 1
                break
            }
            offset = values[0] * HOUR
            length = lengths[0]
            parsed = OffsetFields.H
            if (lengths[1] != 2 || values[1] > 59) break
            offset += values[1] * MINUTE
            length += 1 + lengths[1]
            parsed = OffsetFields.HM
            if (lengths[2] != 2 || values[2] > 59) break
            offset += values[2] * SECOND
            length += 1 + lengths[2]
            parsed = OffsetFields.HMS
        } while (false)
        if (parsed == null || parsed.ordinal < minFields.ordinal) { pos.errorIndex = start; return 0 }
        pos.index = start + length
        return offset
    }
    private fun charAt(text: String, index: Int): Char {
        if (index !in text.indices) throw IndexOutOfBoundsException()
        return text[index]
    }
}
internal expect fun timeZoneOffsetAssertionsEnabled(): Boolean
