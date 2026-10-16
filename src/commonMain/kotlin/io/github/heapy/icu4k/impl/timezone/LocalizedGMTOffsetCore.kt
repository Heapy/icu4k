// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2011-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.Volatile

/** TimeZoneFormat's localized numeric GMT configuration, formatting and parsing. */
internal class LocalizedGMTOffsetCore private constructor() {
    enum class GMTOffsetPatternType(val defaultPattern: String, val required: String, val positive: Boolean) {
        POSITIVE_HM("+H:mm","Hm",true), POSITIVE_HMS("+H:mm:ss","Hms",true),
        NEGATIVE_HM("-H:mm","Hm",false), NEGATIVE_HMS("-H:mm:ss","Hms",false),
        POSITIVE_H("+H","H",true), NEGATIVE_H("-H","H",false)
    }
    private class Field(val type: Char, val width: Int)
    private lateinit var gmtPattern: String
    private lateinit var prefix: String
    private lateinit var suffix: String
    private lateinit var patterns: Array<String>
    private lateinit var items: Array<Array<Any>>
    private var digits = Array(10) { it.toString() }
    private var zero = "GMT"
    private var abutting = false
    @Volatile private var frozen = false

    constructor(locale: ULocale?) : this() {
        val actual = locale!!
        var pattern: String? = null
        var hourFormats: String? = null
        try {
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_ZONE_BASE_NAME,actual) as ICUResourceBundle
            try { pattern = bundle.getStringWithFallback("zoneStrings/gmtFormat") } catch (_: MissingResourceException) { }
            try { hourFormats = bundle.getStringWithFallback("zoneStrings/hourFormat") } catch (_: MissingResourceException) { }
            try { zero = bundle.getStringWithFallback("zoneStrings/gmtZeroFormat") } catch (_: MissingResourceException) { }
        } catch (_: MissingResourceException) { }
        initGMTPattern(pattern ?: "GMT{0}")
        val formats = hourFormats
        patterns = if (formats != null) {
            val split = formats.indexOf(';')
            val positive = if (split < 0) formats else formats.substring(0,split)
            val negative = if (split < 0) throw IndexOutOfBoundsException() else formats.substring(split+1)
            arrayOf(positive,expand(positive),negative,expand(negative),truncate(positive),truncate(negative))
        } else GMTOffsetPatternType.entries.map { it.defaultPattern }.toTypedArray()
        items = Array(patterns.size) { parsePattern(patterns[it],GMTOffsetPatternType.entries[it].required) }
        checkAbutting()
        val numbering = NumberingSystem.getInstance(actual)
        if (!numbering.isAlgorithmic()) digits = codePoints(numbering.getDescription()!!)
    }
    fun getGMTPattern(): String = gmtPattern
    fun getGMTOffsetPattern(type: GMTOffsetPatternType?): String = patterns[type!!.ordinal]
    fun getGMTOffsetDigits(): String = digits.joinToString("")
    fun getGMTZeroFormat(): String = zero
    fun isFrozen(): Boolean = frozen
    fun freeze(): LocalizedGMTOffsetCore { frozen = true; return this }
    fun clone(): LocalizedGMTOffsetCore = LocalizedGMTOffsetCore().also {
        it.gmtPattern = gmtPattern; it.prefix = prefix; it.suffix = suffix
        // TimeZoneFormat's shallow clone intentionally shares the pattern arrays.
        it.patterns = patterns; it.items = items; it.digits = digits
        it.zero = zero; it.abutting = abutting; it.frozen = frozen
    }
    fun cloneAsThawed(): LocalizedGMTOffsetCore = clone().also { it.frozen = false }
    private fun checkMutable() { if (frozen) throw UnsupportedOperationException("Attempt to modify frozen object") }
    fun setGMTPattern(pattern: String?): LocalizedGMTOffsetCore { checkMutable(); initGMTPattern(pattern!!); return this }
    fun setGMTOffsetPattern(type: GMTOffsetPatternType?, pattern: String?): LocalizedGMTOffsetCore {
        checkMutable()
        if (pattern == null) throw NullPointerException("Null GMT offset pattern")
        val parsed = parsePattern(pattern,type!!.required)
        patterns[type.ordinal] = pattern
        items[type.ordinal] = parsed
        checkAbutting()
        return this
    }
    fun setGMTOffsetDigits(value: String?): LocalizedGMTOffsetCore {
        checkMutable()
        if (value == null) throw NullPointerException("Null GMT offset digits")
        val parsed = codePoints(value)
        if (parsed.size != 10) throw IllegalArgumentException("Length of digits must be 10")
        digits = parsed
        return this
    }
    fun setGMTZeroFormat(value: String?): LocalizedGMTOffsetCore {
        checkMutable()
        if (value == null) throw NullPointerException("Null GMT zero format")
        if (value.isEmpty()) throw IllegalArgumentException("Empty GMT zero format")
        zero = value
        return this
    }
    fun formatOffsetLocalizedGMT(offset: Int): String = format(offset,false)
    fun formatOffsetShortLocalizedGMT(offset: Int): String = format(offset,true)
    private fun format(value: Int, short: Boolean): String {
        var offset = value
        val positive = offset >= 0
        if (!positive) offset = -offset
        val hour = offset / HOUR
        offset %= HOUR
        val minute = offset / MINUTE
        offset %= MINUTE
        val second = offset / SECOND
        if (hour > 23 || minute > 59 || second > 59) throw IllegalArgumentException("Offset out of range :$offset")
        val type = if (positive) {
            if (second != 0) GMTOffsetPatternType.POSITIVE_HMS else if (minute != 0 || !short) GMTOffsetPatternType.POSITIVE_HM else GMTOffsetPatternType.POSITIVE_H
        } else {
            if (second != 0) GMTOffsetPatternType.NEGATIVE_HMS else if (minute != 0 || !short) GMTOffsetPatternType.NEGATIVE_HM else GMTOffsetPatternType.NEGATIVE_H
        }
        return buildString {
            append(prefix)
            for (item in items[type.ordinal]) when (item) {
                is String -> append(item)
                is Field -> when(item.type) {
                    'H' -> appendDigits(this,hour,if (short) 1 else 2)
                    'm' -> appendDigits(this,minute,2)
                    's' -> appendDigits(this,second,2)
                }
            }
            append(suffix)
        }
    }
    private fun appendDigits(out: StringBuilder, n: Int, minimum: Int) {
        if (timeZoneOffsetAssertionsEnabled() && (n < 0 || n >= 60)) throw AssertionError()
        val count = if (n >= 10) 2 else 1
        repeat((minimum-count).coerceAtLeast(0)) { out.append(digits[0]) }
        if (count == 2) out.append(digits[n/10])
        val last = n % 10
        if (last !in digits.indices) throw IndexOutOfBoundsException()
        out.append(digits[last])
    }
    @Suppress("UNUSED_PARAMETER") // ICU4J currently uses identical parsing rules for both widths.
    fun parseOffsetLocalizedGMT(text: String?, pos: ParsePosition?, short: Boolean = false, hasDigitOffset: Output<Boolean>? = null): Int {
        val start = pos!!.index
        val length = intArrayOf(0)
        if (hasDigitOffset != null) hasDigitOffset.value = false
        val input = text!!
        var offset = parseLocalizedPattern(input,start,length)
        if (length[0] > 0) {
            if (hasDigitOffset != null) hasDigitOffset.value = true
            pos.index = start + length[0]
            return offset
        }
        offset = parseDefault(input,start,length)
        if (length[0] > 0) {
            if (hasDigitOffset != null) hasDigitOffset.value = true
            pos.index = start + length[0]
            return offset
        }
        if (matches(input,start,zero)) { pos.index = start + zero.length; return 0 }
        for (alternative in ALTERNATIVES) if (matches(input,start,alternative)) { pos.index = start + alternative.length; return 0 }
        pos.errorIndex = start
        return 0
    }
    private fun parseLocalizedPattern(text: String, start: Int, length: IntArray): Int {
        var index = start
        var offset = 0
        var parsed = false
        do {
            if (prefix.isNotEmpty() && !matches(text,index,prefix)) break
            index += prefix.length
            val offsetLength = intArrayOf(0)
            offset = parseFields(text,index,offsetLength)
            if (offsetLength[0] == 0) break
            index += offsetLength[0]
            if (suffix.isNotEmpty() && !matches(text,index,suffix)) break
            index += suffix.length
            parsed = true
        } while (false)
        length[0] = if (parsed) index-start else 0
        return offset
    }
    private fun parseFields(text: String, start: Int, length: IntArray): Int {
        length[0] = 0
        var out = 0
        var sign = 1
        val fields = IntArray(3)
        val selected = IntArray(3)
        for (type in PARSE_TYPES) {
            out = parseWithPattern(text,start,items[type.ordinal],false,fields)
            if (out > 0) { sign = if (type.positive) 1 else -1; fields.copyInto(selected); break }
        }
        if (out > 0 && abutting) {
            var other = 0
            var otherSign = 1
            for (type in PARSE_TYPES) {
                other = parseWithPattern(text,start,items[type.ordinal],true,fields)
                if (other > 0) { otherSign = if (type.positive) 1 else -1; break }
            }
            if (other > out) { out = other; sign = otherSign; fields.copyInto(selected) }
        }
        length[0] = out
        return if (out > 0) ((selected[0]*60+selected[1])*60+selected[2])*SECOND*sign else 0
    }
    private fun parseWithPattern(text: String, start: Int, pattern: Array<Any>, singleHour: Boolean, fields: IntArray): Int {
        fields.fill(0)
        val values = IntArray(3)
        var index = start
        val length = intArrayOf(0)
        for ((i,item) in pattern.withIndex()) {
            when(item) {
                is String -> {
                    var size = item.length
                    var patternStart = 0
                    if (i == 0 && index < text.length && !PatternProps.isWhiteSpace(codePointAt(text,index))) {
                        while (size > 0) {
                            val cp = codePointAt(item,patternStart)
                            if (!PatternProps.isWhiteSpace(cp)) break
                            val count = charCount(cp)
                            size -= count; patternStart += count
                        }
                    }
                    if (!regionMatches(text,index,item,patternStart,size)) return 0
                    index += size
                }
                is Field -> {
                    val field = when(item.type) { 'H' -> 0; 'm' -> 1; else -> 2 }
                    values[field] = parseField(text,index,if (field == 0) 1 else 2,if (field == 0 && singleHour) 1 else 2,0,if (field == 0) 23 else 59,length)
                    if (length[0] == 0) return 0
                    index += length[0]
                }
            }
        }
        values.copyInto(fields)
        return index-start
    }
    private fun parseDefault(text: String, start: Int, length: IntArray): Int {
        var index = start
        var offset = 0
        var parsed = 0
        do {
            val alternative = ALTERNATIVES.firstOrNull { matches(text,index,it) } ?: break
            index += alternative.length
            if (index + 1 >= text.length) break
            val sign = when(text[index]) { '+' -> 1; '-' -> -1; else -> break }
            index++
            val separatedLength = intArrayOf(0)
            val separated = parseDefaultFields(text,index,':',separatedLength)
            if (separatedLength[0] == text.length-index) {
                offset = separated*sign; index += separatedLength[0]
            } else {
                val abuttingLength = intArrayOf(0)
                val abuttingOffset = parseAbutting(text,index,abuttingLength)
                if (separatedLength[0] > abuttingLength[0]) { offset = separated*sign; index += separatedLength[0] }
                else { offset = abuttingOffset*sign; index += abuttingLength[0] }
            }
            parsed = index-start
        } while (false)
        length[0] = parsed
        return offset
    }
    private fun parseDefaultFields(text: String, start: Int, separator: Char, length: IntArray): Int {
        var index = start
        val parsed = intArrayOf(0)
        var hour = 0; var minute = 0; var second = 0
        do {
            hour = parseField(text,index,1,2,0,23,parsed)
            if (parsed[0] == 0) break
            index += parsed[0]
            if (index + 1 < text.length && text[index] == separator) {
                minute = parseField(text,index+1,2,2,0,59,parsed)
                if (parsed[0] == 0) break
                index += 1+parsed[0]
                if (index + 1 < text.length && text[index] == separator) {
                    second = parseField(text,index+1,2,2,0,59,parsed)
                    if (parsed[0] == 0) break
                    index += 1+parsed[0]
                }
            }
        } while (false)
        length[0] = index-start
        return if (index == start) 0 else hour*HOUR+minute*MINUTE+second*SECOND
    }
    private fun parseAbutting(text: String, start: Int, length: IntArray): Int {
        val digits = IntArray(6)
        val ends = IntArray(6)
        var index = start
        val parsed = intArrayOf(0)
        var count = 0
        for (i in digits.indices) {
            digits[i] = parseDigit(text,index,parsed)
            if (digits[i] < 0) break
            index += parsed[0]; ends[i] = index-start; count++
        }
        if (count == 0) { length[0] = 0; return 0 }
        while (count > 0) {
            var hour = 0; var minute = 0; var second = 0
            when(count) {
                1 -> hour = digits[0]
                2 -> hour = digits[0]*10+digits[1]
                3 -> { hour = digits[0]; minute = digits[1]*10+digits[2] }
                4 -> { hour = digits[0]*10+digits[1]; minute = digits[2]*10+digits[3] }
                5 -> { hour = digits[0]; minute = digits[1]*10+digits[2]; second = digits[3]*10+digits[4] }
                6 -> { hour = digits[0]*10+digits[1]; minute = digits[2]*10+digits[3]; second = digits[4]*10+digits[5] }
            }
            if (hour <= 23 && minute <= 59 && second <= 59) { length[0] = ends[count-1]; return hour*HOUR+minute*MINUTE+second*SECOND }
            count--
        }
        return 0
    }
    private fun parseField(text: String, start: Int, minimum: Int, maximum: Int, minValue: Int, maxValue: Int, length: IntArray): Int {
        length[0] = 0
        var value = 0
        var count = 0
        var index = start
        val parsed = intArrayOf(0)
        while (index < text.length && count < maximum) {
            val digit = parseDigit(text,index,parsed)
            if (digit < 0) break
            val next = value*10+digit
            if (next > maxValue) break
            value = next; count++; index += parsed[0]
        }
        if (count < minimum || value < minValue) return -1
        length[0] = index-start
        return value
    }
    private fun parseDigit(text: String, start: Int, length: IntArray): Int {
        length[0] = 0
        if (start >= text.length) return -1
        val cp = codePointAt(text,start)
        var digit = digits.indexOfFirst { codePointAt(it,0) == cp }
        if (digit < 0) digit = UCharacter.digit(cp)
        if (digit >= 0) length[0] = charCount(cp)
        return digit
    }
    private fun initGMTPattern(pattern: String) {
        val index = pattern.indexOf("{0}")
        if (index < 0) throw IllegalArgumentException("Bad localized GMT pattern: $pattern")
        gmtPattern = pattern
        prefix = unquote(pattern.substring(0,index))
        suffix = unquote(pattern.substring(index+3))
    }
    private fun checkAbutting() {
        abutting = false
        for (pattern in items) {
            var afterHour = false
            for (item in pattern) if (item is Field) {
                if (afterHour) abutting = true else if (item.type == 'H') afterHour = true
            } else if (afterHour) break
        }
    }
    companion object {
        private const val HOUR = 3_600_000
        private const val MINUTE = 60_000
        private const val SECOND = 1_000
        private val ALTERNATIVES = arrayOf("GMT","UTC","UT")
        private val PARSE_TYPES = arrayOf(GMTOffsetPatternType.POSITIVE_HMS,GMTOffsetPatternType.NEGATIVE_HMS,GMTOffsetPatternType.POSITIVE_HM,GMTOffsetPatternType.NEGATIVE_HM,GMTOffsetPatternType.POSITIVE_H,GMTOffsetPatternType.NEGATIVE_H)
        private fun charCount(cp: Int): Int = if (cp >= 0x10000) 2 else 1
        private fun codePointAt(text: String, index: Int, limit: Int = text.length): Int {
            if (index < 0 || index >= limit) throw IndexOutOfBoundsException()
            val first = text[index].code
            if (first in 0xd800..0xdbff && index+1 < limit) {
                val second = text[index+1].code
                if (second in 0xdc00..0xdfff) return ((first-0xd800) shl 10)+second-0xdc00+0x10000
            }
            return first
        }
        private fun codePoints(text: String): Array<String> {
            val result = mutableListOf<String>()
            var index = 0
            while (index < text.length) { val count = charCount(codePointAt(text,index)); result.add(text.substring(index,index+count)); index += count }
            return result.toTypedArray()
        }
        private fun matches(text: String, start: Int, pattern: String): Boolean = regionMatches(text,start,pattern,0,pattern.length)
        private fun regionMatches(text: String, start: Int, pattern: String, patternStart: Int, length: Int): Boolean =
            localizedGMTRegionMatches(text,start,pattern,patternStart,length)
        private fun unquote(text: String): String {
            if ('\'' !in text) return text
            var previous = false
            return buildString { for (c in text) if (c == '\'') { if (previous) { append(c); previous = false } else previous = true } else { previous = false; append(c) } }
        }
        private fun parsePattern(pattern: String, letters: String): Array<Any> {
            var previous = false
            var quoted = false
            val text = StringBuilder()
            var type = '\u0000'
            var width = 1
            var invalid = false
            val items = mutableListOf<Any>()
            val seen = BooleanArray(letters.length)
            fun finish(): Boolean {
                if (width != 1 && width != 2) return false
                items.add(Field(type,width)); return true
            }
            for (ch in pattern) {
                if (ch == '\'') {
                    if (previous) { text.append('\''); previous = false }
                    else {
                        previous = true
                        if (type != '\u0000') { if (!finish()) { invalid = true; break }; type = '\u0000' }
                    }
                    quoted = !quoted
                } else {
                    previous = false
                    if (quoted) text.append(ch)
                    else {
                        val field = letters.indexOf(ch)
                        if (field >= 0) {
                            if (ch == type) width++
                            else {
                                if (type == '\u0000') { if (text.isNotEmpty()) { items.add(text.toString()); text.setLength(0) } }
                                else if (!finish()) { invalid = true; break }
                                type = ch; width = 1; seen[field] = true
                            }
                        } else {
                            if (type != '\u0000') { if (!finish()) { invalid = true; break }; type = '\u0000' }
                            text.append(ch)
                        }
                    }
                }
            }
            if (!invalid) {
                if (type == '\u0000') { if (text.isNotEmpty()) items.add(text.toString()) }
                else if (!finish()) invalid = true
            }
            if (invalid || seen.count { it } != letters.length) throw IllegalStateException("Bad localized GMT offset pattern: $pattern")
            return items.toTypedArray()
        }
        private fun expand(pattern: String): String {
            val minute = pattern.indexOf("mm")
            if (minute < 0) throw RuntimeException("Bad time zone hour pattern data")
            val hour = pattern.substring(0,minute).lastIndexOf('H')
            val separator = if (hour >= 0) pattern.substring(hour+1,minute) else ":"
            return pattern.substring(0,minute+2)+separator+"ss"+pattern.substring(minute+2)
        }
        private fun truncate(pattern: String): String {
            val minute = pattern.indexOf("mm")
            if (minute < 0) throw RuntimeException("Bad time zone hour pattern data")
            val double = pattern.substring(0,minute).lastIndexOf("HH")
            if (double >= 0) return pattern.substring(0,double+2)
            val hour = pattern.substring(0,minute).lastIndexOf('H')
            if (hour >= 0) return pattern.substring(0,hour+1)
            throw RuntimeException("Bad time zone hour pattern data")
        }
        internal fun portableRegionMatches(text: String, start: Int, pattern: String, patternStart: Int, length: Int): Boolean {
            if (start < 0 || patternStart < 0 || start.toLong()+length > text.length || patternStart.toLong()+length > pattern.length) return false
            var i = 0
            while (i < length) {
                val a = codePointAt(text,start+i,start+length)
                val b = codePointAt(pattern,patternStart+i,patternStart+length)
                if (a != b) {
                    val upperA = UCharacter.toUpperCase(a)
                    val upperB = UCharacter.toUpperCase(b)
                    if (upperA != upperB && UCharacter.toLowerCase(upperA) != UCharacter.toLowerCase(upperB)) return false
                }
                i += charCount(a)
            }
            return true
        }
    }
}
internal expect fun localizedGMTRegionMatches(text: String, start: Int, pattern: String, patternStart: Int, length: Int): Boolean
