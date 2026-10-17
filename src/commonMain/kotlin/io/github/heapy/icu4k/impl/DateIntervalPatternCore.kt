// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.text.DateIntervalInfo.PatternInfo
import io.github.heapy.icu4k.util.*

/** DateIntervalFormat's CLDR skeleton selection and interval pattern generation. */
internal class DateIntervalPatternCore(private val owner: Owner) {
    interface Owner {
        val info: DateIntervalInfo
        val fullPattern: String?
        val locale: ULocale?
        var skeleton: String?
        var datePattern: String?
        var timePattern: String?
        var dateTimeFormat: String?
    }
    private data class SkeletonMatch(val skeleton: String, val bestSkeleton: String)
    private val info get() = owner.info
    fun initialize(fullPattern: String? = owner.fullPattern): MutableMap<String, PatternInfo> {
        val generator = DateTimePatternGenerator.getInstance(owner.locale)
        if (owner.skeleton == null) owner.skeleton = generator.getSkeleton(fullPattern)
        var skeleton = normalizeHourMetacharacters(owner.skeleton!!, owner.locale)
        val patterns = intervalPatternMap<PatternInfo>()
        val date = StringBuilder(); val normalizedDate = StringBuilder()
        val time = StringBuilder(); val normalizedTime = StringBuilder()
        getDateTimeSkeleton(skeleton, date, normalizedDate, time, normalizedTime)
        val dateSkeleton = date.toString()
        val timeSkeleton = time.toString()
        if (time.isNotEmpty() && date.isNotEmpty()) {
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, owner.locale) as ICUResourceBundle
            val value = bundle.getWithFallback("calendar/gregorian/DateTimePatterns").get(8)
            owner.dateTimeFormat = if (value.getType() == UResourceBundle.STRING) value.getString() else value.getString(0)
        }
        val found = separate(normalizedDate.toString(), normalizedTime.toString(), patterns, generator)
        if (!found) {
            if (time.isNotEmpty() && date.isEmpty()) addTimeFallback(timeSkeleton, patterns, generator)
            return nonNullKeys(patterns)
        }
        if (time.isNotEmpty()) {
            if (date.isEmpty()) addTimeFallback(timeSkeleton, patterns, generator)
            else {
                for (field in intArrayOf(Calendar.DATE, Calendar.MONTH, Calendar.YEAR, Calendar.ERA)) {
                    if (!fieldExistsInSkeleton(field, dateSkeleton)) {
                        skeleton = letter(field) + skeleton
                        patterns[letter(field)] = PatternInfo(null, generator.getBestPattern(skeleton), info.getDefaultOrder())
                    }
                }
                if (owner.dateTimeFormat == null) owner.dateTimeFormat = "{1} {0}"
                val datePattern = generator.getBestPattern(dateSkeleton)
                for (field in intArrayOf(Calendar.AM_PM, Calendar.HOUR, Calendar.MINUTE)) {
                    val previous = patterns[letter(field)] ?: continue
                    val timePattern = previous.getFirstPart() + previous.getSecondPart()
                    val pattern = SimpleFormatterImpl.formatRawPattern(owner.dateTimeFormat!!, 2, 2, timePattern, datePattern)
                    patterns[letter(field)] = DateIntervalInfo.genPatternInfo(pattern, previous.firstDateInPtnIsLaterDate())
                }
            }
        }
        return nonNullKeys(patterns)
    }
    // All keys inserted above are fixed non-null calendar field letters.
    @Suppress("UNCHECKED_CAST")
    private fun nonNullKeys(map: MutableMap<String?, PatternInfo>): MutableMap<String, PatternInfo> = map as MutableMap<String, PatternInfo>
    private fun addTimeFallback(time: String, patterns: MutableMap<String?, PatternInfo>, generator: DateTimePatternGenerator) {
        val skeleton = DateFormat.YEAR_NUM_MONTH_DAY + time
        val pattern = PatternInfo(null, generator.getBestPattern(skeleton), info.getDefaultOrder())
        for (field in intArrayOf(Calendar.DATE, Calendar.MONTH, Calendar.YEAR)) patterns[letter(field)] = pattern
        patterns[letter(Calendar.ERA)] = PatternInfo(null, generator.getBestPattern(skeleton + "G"), info.getDefaultOrder())
    }
    private fun separate(date: String, time: String, patterns: MutableMap<String?, PatternInfo>, generator: DateTimePatternGenerator): Boolean {
        var skeleton = if (time.isNotEmpty()) time else date
        val match = info.getBestSkeleton(skeleton)
        var best = match.bestMatchSkeleton!!
        val difference = match.bestMatchDistanceInfo
        if (date.isNotEmpty()) owner.datePattern = generator.getBestPattern(date)
        if (time.isNotEmpty()) owner.timePattern = generator.getBestPattern(time)
        if (difference == -1) return false
        if (time.isEmpty()) {
            generate(Calendar.DATE, skeleton, best, difference, patterns)
            val changed = generate(Calendar.MONTH, skeleton, best, difference, patterns)
            // Source deliberately assigns these in this order.
            if (changed != null) { best = changed.skeleton; skeleton = changed.bestSkeleton }
            generate(Calendar.YEAR, skeleton, best, difference, patterns)
            generate(Calendar.ERA, skeleton, best, difference, patterns)
        } else for (field in intArrayOf(Calendar.MINUTE, Calendar.HOUR, Calendar.AM_PM)) generate(field, skeleton, best, difference, patterns)
        return true
    }
    private fun generate(field: Int, input: String, matched: String, distance: Int, patterns: MutableMap<String?, PatternInfo>): SkeletonMatch? {
        var skeleton = input; var best = matched; var difference = distance
        var result: SkeletonMatch? = null
        var pattern = info.getIntervalPattern(best, field)
        if (pattern == null) {
            if (SimpleDateFormatPatternCore.isFieldUnitIgnored(best, field)) {
                patterns[letter(field)] = PatternInfo(owner.fullPattern, null, info.getDefaultOrder())
                return null
            }
            if (field == Calendar.AM_PM) {
                pattern = info.getIntervalPattern(best, Calendar.HOUR)
                if (pattern != null) patterns[letter(field)] = adjusted(skeleton, best, pattern, difference)
                return null
            }
            best = letter(field) + best; skeleton = letter(field) + skeleton
            pattern = info.getIntervalPattern(best, field)
            if (pattern == null && difference == 0) {
                val match = info.getBestSkeleton(skeleton)
                difference = match.bestMatchDistanceInfo
                if (match.bestMatchSkeleton!!.isNotEmpty() && difference != -1) {
                    pattern = info.getIntervalPattern(match.bestMatchSkeleton, field)
                    best = match.bestMatchSkeleton
                }
            }
            if (pattern != null) result = SkeletonMatch(skeleton, best)
        }
        if (pattern != null) patterns[letter(field)] = if (difference != 0) adjusted(skeleton, best, pattern, difference) else pattern
        return result
    }
    private fun adjusted(skeleton: String, best: String, pattern: PatternInfo, difference: Int): PatternInfo {
        val suppress = owner.skeleton!!.indexOf('J') != -1
        return PatternInfo(adjustFieldWidth(skeleton, best, pattern.getFirstPart(), difference, suppress), adjustFieldWidth(skeleton, best, pattern.getSecondPart(), difference, suppress), pattern.firstDateInPtnIsLaterDate())
    }
    companion object {
        private fun letter(field: Int) = DateIntervalInfo.CALENDAR_FIELD_TO_PATTERN_LETTER[field]
        internal fun fieldExistsInSkeleton(field: Int, skeleton: String): Boolean {
            val char = letter(field)
            return skeleton.contains(char) || when (char) { "M" -> skeleton.contains('L'); "y" -> skeleton.any { it in "UYur" }; else -> false }
        }
        internal fun normalizeHourMetacharacters(skeleton: String, locale: ULocale?): String {
            val result = StringBuilder(skeleton)
            var hour = '\u0000'; var dayPeriod = '\u0000'
            var hourStart = 0; var hourLength = 0; var periodStart = 0; var periodLength = 0
            for (i in result.indices) {
                val c = result[i]
                if (c in "jJChHkK") {
                    if (hour == '\u0000') { hour = c; hourStart = i }
                    hourLength++
                } else if (c in "abB") {
                    if (dayPeriod == '\u0000') { dayPeriod = c; periodStart = i }
                    periodLength++
                } else if (hour != '\u0000' && dayPeriod != '\u0000') break
            }
            if (hour != '\u0000') {
                var converted = DateTimePatternGenerator.getInstance(locale).getBestPattern(hour.toString())!!
                while (true) {
                    val first = converted.indexOf('\'')
                    if (first == -1) break
                    val second = converted.indexOf('\'', first + 1).let { if (it == -1) first else it }
                    converted = converted.substring(0, first) + converted.substring(second + 1)
                }
                val hourChar = when { 'h' in converted -> 'h'; 'K' in converted -> 'K'; 'k' in converted -> 'k'; else -> 'H' }
                dayPeriod = when { 'b' in converted -> 'b'; 'B' in converted -> 'B'; dayPeriod == '\u0000' -> 'a'; else -> dayPeriod }
                val replacement = buildString {
                    append(hourChar)
                    if (hourChar != 'H' && hourChar != 'k') repeat(when { periodLength >= 5 || hourLength >= 5 -> 5; periodLength >= 3 || hourLength >= 3 -> 3; else -> 1 }) { append(dayPeriod) }
                }
                result.deleteRange(hourStart, hourStart + hourLength).insert(hourStart, replacement)
                if (periodStart > hourStart) periodStart += replacement.length - hourLength
                result.deleteRange(periodStart, periodStart + periodLength)
            }
            return result.toString()
        }
        internal fun getDateTimeSkeleton(skeleton: String, date: StringBuilder, normalizedDate: StringBuilder, time: StringBuilder, normalizedTime: StringBuilder) {
            var e = 0; var d = 0; var m = 0; var y = 0; var minute = 0; var v = 0; var z = 0
            var hour = '\u0000'
            for (c in skeleton) when (c) {
                'E' -> { date.append(c); e++ }; 'd' -> { date.append(c); d++ }
                'M' -> { date.append(c); m++ }; 'y' -> { date.append(c); y++ }
                in "GYuQqLlWwDFgecUr" -> { date.append(c); normalizedDate.append(c) }
                in "hHkK" -> { time.append(c); if (hour == '\u0000') hour = c }
                'm' -> { time.append(c); minute++ }; 'z' -> { time.append(c); z++ }; 'v' -> { time.append(c); v++ }
                in "aVZjsSAbB" -> { time.append(c); normalizedTime.append(c) }
            }
            repeat(y) { normalizedDate.append('y') }
            repeat(if (m < 3) minOf(m, 1) else minOf(m, 5)) { normalizedDate.append('M') }
            repeat(if (e <= 3) minOf(e, 1) else minOf(e, 5)) { normalizedDate.append('E') }
            if (d != 0) normalizedDate.append('d')
            if (hour != '\u0000') normalizedTime.append(hour)
            if (minute != 0) normalizedTime.append('m')
            if (z != 0) normalizedTime.append('z')
            if (v != 0) normalizedTime.append('v')
        }
        internal fun adjustFieldWidth(inputSkeleton: String, bestSkeleton: String, inputPattern: String?, difference: Int, suppressDayPeriod: Boolean): String? {
            if (inputPattern == null) return null
            val inputWidths = IntArray(58); val bestWidths = IntArray(58)
            DateIntervalInfo.parseSkeleton(inputSkeleton, inputWidths)
            DateIntervalInfo.parseSkeleton(bestSkeleton, bestWidths)
            var pattern: String = inputPattern
            if (suppressDayPeriod) {
                for (candidate in listOf(" a", "\u00a0a", "\u202fa", "a ", "a\u00a0", "a\u202f")) if (candidate in pattern) {
                    pattern = findReplaceInPattern(pattern, candidate, ""); break
                }
                pattern = findReplaceInPattern(pattern, "a", "")
            }
            if (difference == 2) for ((from, to) in listOf('v' to 'z', 'h' to 'K', 'H' to 'k', 'a' to 'b')) if (to in inputSkeleton) pattern = findReplaceInPattern(pattern, from.toString(), to.toString())
            for (c in "ab") if (c in pattern && bestWidths[c - 'A'] == 0) bestWidths[c - 'A'] = 1
            val result = StringBuilder(pattern)
            var inQuote = false; var previous = '\u0000'; var count = 0; var i = 0
            fun extra(): Int {
                val c = if (previous == 'L') 'M' else previous
                val width = bestWidths[c - 'A']; val input = inputWidths[c - 'A']
                return if (width == count && input > width) input - width else 0
            }
            while (i < result.length) {
                val c = result[i]
                if (c != previous && count > 0) {
                    val extra = extra()
                    repeat(extra) { result.insert(i, previous) }
                    i += extra; count = 0
                }
                if (c == '\'') {
                    if (i + 1 < result.length && result[i + 1] == '\'') i++ else inQuote = !inQuote
                } else if (!inQuote && (c in 'a'..'z' || c in 'A'..'Z')) { previous = c; count++ }
                i++
            }
            if (count > 0) repeat(extra()) { result.append(previous) }
            return result.toString()
        }
        internal fun findReplaceInPattern(target: String, old: String, replacement: String): String {
            var first = target.indexOf('\'')
            if (first < 0) return target.replace(old, replacement)
            return buildString {
                var source = target
                while (first >= 0) {
                    val second = source.indexOf('\'', first + 1).let { if (it < 0) source.length - 1 else it }
                    append(source.substring(0, first).replace(old, replacement))
                    append(source.substring(first, second + 1))
                    source = source.substring(second + 1); first = source.indexOf('\'')
                }
                append(source.replace(old, replacement))
            }
        }
    }
}
