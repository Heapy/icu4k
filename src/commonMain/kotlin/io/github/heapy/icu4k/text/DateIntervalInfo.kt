// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.Volatile
import kotlin.math.abs

/** Locale interval patterns, default endpoint order and copy-on-write frozen data. */
open class DateIntervalInfo : ICUShallowCloneableBase, ICUSerializable, Freezable<DateIntervalInfo> {
    class PatternInfo(
        private val fIntervalPatternFirstPart: String?,
        private val fIntervalPatternSecondPart: String?,
        private val fFirstDateInPtnIsLaterDate: Boolean,
    ) : ICUShallowCloneableBase(), ICUSerializable {
        fun getFirstPart(): String? = fIntervalPatternFirstPart
        fun getSecondPart(): String? = fIntervalPatternSecondPart
        fun firstDateInPtnIsLaterDate(): Boolean = fFirstDateInPtnIsLaterDate
        protected override fun clone(): Any = cloneHost() ?: PatternInfo(fIntervalPatternFirstPart, fIntervalPatternSecondPart, fFirstDateInPtnIsLaterDate)
        override fun equals(other: Any?): Boolean = other is PatternInfo && fIntervalPatternFirstPart == other.fIntervalPatternFirstPart && fIntervalPatternSecondPart == other.fIntervalPatternSecondPart && fFirstDateInPtnIsLaterDate == other.fFirstDateInPtnIsLaterDate
        override fun hashCode(): Int = ((fIntervalPatternFirstPart?.hashCode() ?: 0) xor (fIntervalPatternSecondPart?.hashCode() ?: 0)) xor if (fFirstDateInPtnIsLaterDate) -1 else 0
        override fun toString(): String = "{first=«$fIntervalPatternFirstPart», second=«$fIntervalPatternSecondPart», reversed:$fFirstDateInPtnIsLaterDate}"
        companion object { private const val serialVersionUID = 1L }
    }
    private var fFallbackIntervalPattern: String? = null
    private var fFirstDateInPtnIsLaterDate = false
    private var fIntervalPatterns: MutableMap<String?, MutableMap<String?, PatternInfo>>? = null
    @kotlin.jvm.Transient @Volatile private var frozen = false
    @kotlin.jvm.Transient private var fIntervalPatternsReadOnly = false

    constructor() : super() { fIntervalPatterns = intervalPatternMap(); fFallbackIntervalPattern = "{0} – {1}" }
    constructor(locale: ULocale?) : super() {
        val key = locale!!.toString()
        val cached = cache.get(key)
        if (cached == null) {
            setup(locale)
            fIntervalPatternsReadOnly = true
            cache.put(key, clone().freeze())
        } else initializeFromReadOnlyPatterns(cached)
    }
    protected constructor(other: DateIntervalInfo, @Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super() {
        fFallbackIntervalPattern = other.fFallbackIntervalPattern; fFirstDateInPtnIsLaterDate = other.fFirstDateInPtnIsLaterDate
        fIntervalPatterns = other.fIntervalPatterns; frozen = other.frozen; fIntervalPatternsReadOnly = other.fIntervalPatternsReadOnly
    }
    private fun initializeFromReadOnlyPatterns(other: DateIntervalInfo) {
        fFallbackIntervalPattern = other.fFallbackIntervalPattern; fFirstDateInPtnIsLaterDate = other.fFirstDateInPtnIsLaterDate
        fIntervalPatterns = other.fIntervalPatterns; fIntervalPatternsReadOnly = true
    }
    private fun setup(locale: ULocale) {
        fIntervalPatterns = intervalPatternMap(19)
        fFallbackIntervalPattern = "{0} – {1}"
        try {
            var type: String? = locale.getKeywordValue("calendar") ?: Calendar.getKeywordValuesForLocale("calendar", locale, true)[0]
            if (type == null) type = "gregorian"
            val sink = DateIntervalSink()
            val resource = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
            setFallbackIntervalPattern(resource.getStringWithFallback("calendar/$type/intervalFormats/fallback"))
            val loaded = HashSet<String>()
            while (type != null) {
                if (!loaded.add(type)) throw ICUException("Loop in calendar type fallback: $type")
                resource.getAllItemsWithFallback("calendar/$type", sink)
                type = sink.nextCalendarType; sink.nextCalendarType = null
            }
        } catch (_: MissingResourceException) { }
    }
    private inner class DateIntervalSink : UResource.Sink() {
        var nextCalendarType: String? = null
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val calendars = value.getTable()
            var i = 0
            while (calendars.getKeyAndValue(i++, key, value)) {
                if (!key.contentEquals("intervalFormats")) continue
                if (value.getType() == ICUResourceBundle.ALIAS) {
                    val path = value.getAliasString()
                    if (!path.startsWith("/LOCALE/calendar/") || !path.endsWith("/intervalFormats")) throw ICUException("Malformed 'intervalFormat' alias path: $path")
                    if (path.length - 16 < 17) throw IndexOutOfBoundsException()
                    nextCalendarType = path.substring(17, path.length - 16)
                    break
                } else if (value.getType() == UResourceBundle.TABLE) {
                    val skeletons = value.getTable()
                    var j = 0
                    while (skeletons.getKeyAndValue(j++, key, value)) if (value.getType() == UResourceBundle.TABLE) processSkeleton(key, value)
                    break
                }
            }
        }
        private fun processSkeleton(key: UResource.Key, value: UResource.Value) {
            val skeleton = key.toString()
            val patterns = value.getTable()
            var i = 0
            while (patterns.getKeyAndValue(i++, key, value)) {
                if (value.getType() != UResourceBundle.STRING || key.length != 1) continue
                val letter = key[0]
                if (letter !in "GyMdahHmsSB") continue
                val field = when (letter) { 'H' -> "h"; 'B' -> "a"; else -> key.toString() }
                val existing = fIntervalPatterns!![skeleton]
                if (existing == null || !existing.containsKey(field)) setIntervalPatternInternally(skeleton, field, value.toString())
            }
        }
    }
    open fun setIntervalPattern(skeleton: String?, field: Int, pattern: String?) {
        if (frozen) throw UnsupportedOperationException("no modification is allowed after DII is frozen")
        if (field > Calendar.MILLISECOND) throw IllegalArgumentException("calendar field is larger than MINIMUM_SUPPORTED_CALENDAR_FIELD")
        if (fIntervalPatternsReadOnly) { fIntervalPatterns = clonePatterns(fIntervalPatterns!!); fIntervalPatternsReadOnly = false }
        val info = setIntervalPatternInternally(skeleton, fieldLetter(field), pattern)
        if (field == Calendar.HOUR_OF_DAY) {
            fIntervalPatterns!![skeleton]!!["a"] = info; fIntervalPatterns!![skeleton]!!["h"] = info
        } else if (field == Calendar.DAY_OF_MONTH || field == Calendar.DAY_OF_WEEK) fIntervalPatterns!![skeleton]!!["d"] = info
    }
    private fun setIntervalPatternInternally(skeleton: String?, field: String, pattern: String?): PatternInfo {
        var patterns = fIntervalPatterns!![skeleton]
        val absent = patterns == null
        if (patterns == null) patterns = intervalPatternMap()
        var text = pattern!!
        var order = fFirstDateInPtnIsLaterDate
        if (text.startsWith("latestFirst:")) { order = true; text = text.substring(12) }
        else if (text.startsWith("earliestFirst:")) { order = false; text = text.substring(14) }
        val info = genPatternInfo(text, order)
        patterns[field] = info
        if (absent) fIntervalPatterns!![skeleton] = patterns
        return info
    }
    open fun getIntervalPattern(skeleton: String?, field: Int): PatternInfo? {
        if (field > Calendar.MILLISECOND) throw IllegalArgumentException("no support for field less than MILLISECOND")
        val patterns = fIntervalPatterns!![skeleton]
        return if (patterns != null) patterns[fieldLetter(field)] else null
    }
    open fun getFallbackIntervalPattern(): String? = fFallbackIntervalPattern
    open fun setFallbackIntervalPattern(pattern: String?) {
        if (frozen) throw UnsupportedOperationException("no modification is allowed after DII is frozen")
        val first = pattern!!.indexOf("{0}"); val second = pattern.indexOf("{1}")
        if (first == -1 || second == -1) throw IllegalArgumentException("no pattern {0} or pattern {1} in fallbackPattern")
        if (first > second) fFirstDateInPtnIsLaterDate = true
        fFallbackIntervalPattern = pattern
    }
    open fun getDefaultOrder(): Boolean = fFirstDateInPtnIsLaterDate
    public override fun clone(): DateIntervalInfo = if (frozen) this else cloneUnfrozen()
    private fun cloneUnfrozen(): DateIntervalInfo {
        val copy = cloneHost() as? DateIntervalInfo ?: cloneNative()
        copy.fFallbackIntervalPattern = fFallbackIntervalPattern; copy.fFirstDateInPtnIsLaterDate = fFirstDateInPtnIsLaterDate
        copy.fIntervalPatterns = if (fIntervalPatternsReadOnly) fIntervalPatterns else clonePatterns(fIntervalPatterns!!)
        copy.fIntervalPatternsReadOnly = fIntervalPatternsReadOnly
        copy.frozen = false
        return copy
    }
    protected open fun cloneNative(): DateIntervalInfo = DateIntervalInfo(this, Unit)
    override fun isFrozen(): Boolean = frozen
    override fun freeze(): DateIntervalInfo { fIntervalPatternsReadOnly = true; frozen = true; return this }
    override fun cloneAsThawed(): DateIntervalInfo = cloneUnfrozen()
    override fun equals(other: Any?): Boolean = other is DateIntervalInfo && fIntervalPatterns!! == other.fIntervalPatterns
    override fun hashCode(): Int = fIntervalPatterns!!.hashCode()
    open fun getPatterns(): MutableMap<String?, MutableSet<String?>> = linkedMapOf<String?, MutableSet<String?>>().also { result ->
        for ((key, values) in fIntervalPatterns!!) result[key] = LinkedHashSet(values.keys)
    }
    open fun getRawPatterns(): MutableMap<String?, MutableMap<String?, PatternInfo>> = linkedMapOf<String?, MutableMap<String?, PatternInfo>>().also { result ->
        for ((key, values) in fIntervalPatterns!!) result[key] = LinkedHashMap(values)
    }
    internal fun getBestSkeleton(input: String?): DateIntervalSkeletonMatch {
        var best = input
        var text = input!!
        val widths = IntArray(58); val candidate = IntArray(58)
        var replaced = false
        if (text.any { it == 'z' || it == 'k' || it == 'K' || it == 'a' || it == 'b' }) {
            text = text.replace('z', 'v').replace('k', 'H').replace('K', 'h').replace("a", "").replace("b", "")
            replaced = true
        }
        parseSkeleton(text, widths)
        var bestDistance = Int.MAX_VALUE; var difference = 0
        for (skeleton in fIntervalPatterns!!.keys) {
            candidate.fill(0); parseSkeleton(skeleton, candidate)
            var distance = 0; var fieldDifference = 1
            for (i in widths.indices) {
                val a = widths[i]; val b = candidate[i]
                if (a == b) continue
                if (a == 0 || b == 0) { fieldDifference = -1; distance += 0x1000 }
                else if (i + 0x41 == 'M'.code && ((a <= 2 && b > 2) || (a > 2 && b <= 2))) distance += 0x100
                else distance += abs(a - b)
            }
            if (distance < bestDistance) { best = skeleton; bestDistance = distance; difference = fieldDifference }
            if (distance == 0) { difference = 0; break }
        }
        if (replaced && difference != -1) difference = 2
        return DateIntervalSkeletonMatch(best, difference)
    }
    companion object {
        private const val serialVersionUID = 1L
        private val cache: ICUCache<String, DateIntervalInfo> = SimpleCache()
        internal val CALENDAR_FIELD_TO_PATTERN_LETTER = arrayOf("G", "y", "M", "w", "W", "d", "D", "E", "F", "a", "h", "H", "m", "s", "S", "z", " ", "Y", "e", "u", "g", "A", " ", " ")
        private fun fieldLetter(field: Int): String {
            if (field !in CALENDAR_FIELD_TO_PATTERN_LETTER.indices) throw IndexOutOfBoundsException()
            return CALENDAR_FIELD_TO_PATTERN_LETTER[field]
        }
        private fun clonePatterns(patterns: Map<String?, MutableMap<String?, PatternInfo>>): MutableMap<String?, MutableMap<String?, PatternInfo>> = intervalPatternMap<MutableMap<String?, PatternInfo>>().also { result ->
            for ((key, values) in patterns) result[key] = intervalPatternMap<PatternInfo>().also { copy -> for ((field, value) in values) copy[field] = value }
        }
        @kotlin.jvm.JvmStatic fun genPatternInfo(pattern: String?, laterDateFirst: Boolean): PatternInfo {
            val text = pattern!!
            var inQuote = false; var previous = '\u0000'; var count = 0; var i = 0; var repetition = false
            val repeated = IntArray(58)
            while (i < text.length) {
                val ch = text[i]
                if (ch != previous && count > 0) {
                    val index = previous.code - 0x41
                    if (repeated[index] == 0) repeated[index] = 1 else { repetition = true; break }
                    count = 0
                }
                if (ch == '\'') {
                    if (i + 1 < text.length && text[i + 1] == '\'') i++ else inQuote = !inQuote
                } else if (!inQuote && (ch in 'a'..'z' || ch in 'A'..'Z')) { previous = ch; count++ }
                i++
            }
            if (count > 0 && !repetition && repeated[previous.code - 0x41] == 0) count = 0
            val split = i - count
            return PatternInfo(text.substring(0, split), if (split < text.length) text.substring(split) else null, laterDateFirst)
        }
        internal fun parseSkeleton(skeleton: String?, widths: IntArray) {
            for (ch in skeleton!!) {
                val index = ch.code - 0x41
                if (index !in widths.indices) throw IndexOutOfBoundsException()
                widths[index]++
            }
        }
    }
}
internal class DateIntervalSkeletonMatch(val bestMatchSkeleton: String?, val bestMatchDistanceInfo: Int)
