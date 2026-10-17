// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.uuid.Uuid

/** Mutable SimpleDateFormat owner state. Base DateFormat fields and virtual setters stay on Owner. */
internal class SimpleDateFormatStateCore(private val owner: Owner) {
    interface Owner {
        var calendar: Calendar?
        var numberFormat: NumberFormat?
        fun currentTimeMillis(): Long
        fun setLocale(valid: ULocale?, actual: ULocale?)
        fun setNumberFormatVirtual(format: NumberFormat?)
        fun createNumberFormat(locale: ULocale?): NumberFormat = NumberFormat.createInstance(locale, NumberFormat.NUMBERSTYLE)!!
        fun newFormatKey(): String = "$" + Uuid.random().toString()
    }
    var pattern: String? = null
    var symbols: DateFormatSymbols? = null
    var locale: ULocale? = null
    var numberingOverride: String? = null
    var useFastFormat = false
    var numberFormatters: MutableMap<String, NumberFormat?>? = null
    var overrideMap: MutableMap<Char, String?>? = null
    var defaultCenturyStart: Date? = null
    var defaultCenturyStartYear = 0
    var defaultCenturyBase = 0L
    var serialVersionOnStream = 2
    var flags = SimpleDateFormatPatternCore.Flags(false, false, false)
        private set
    var patternItems: Array<Any>? = null
    var zoneFormat: TimeZoneFormat? = null
    val numbers = SimpleDateFormatNumberCore()
    private val zoneLock = createAvailabilityLock()

    fun initialize() {
        if (locale == null) locale = ULocale.getDefault(ULocale.Category.FORMAT)
        if (symbols == null) symbols = DateFormatSymbols(locale)
        if (owner.calendar == null) owner.calendar = Calendar.getInstance(locale)
        if (owner.numberFormat == null) {
            val system = NumberingSystem.getInstance(locale)
            val digits = system.getDescription()!!
            owner.numberFormat = if (system.isAlgorithmic() || digits.length != 10) NumberFormat.getInstance(locale)
                else DateNumberFormat(locale, digits, system.getName())
        }
        if (owner.numberFormat is DecimalFormat) DateFormat.fixNumberFormatForDates(owner.numberFormat!!)
        defaultCenturyBase = owner.currentTimeMillis()
        owner.setLocale(owner.calendar!!.getLocale(ULocale.VALID_LOCALE), owner.calendar!!.getLocale(ULocale.ACTUAL_LOCALE))
        numbers.initialize(owner.numberFormat)
        parsePattern()
        if (numberingOverride == null && flags.hasHanYearChar && owner.calendar?.getType() == "japanese" && locale?.getLanguage() == "ja") numberingOverride = "y=jpanyear"
        if (numberingOverride != null) initNumberFormatters(locale)
    }
    fun parsePattern() {
        // Source clears all flags even if the subsequently read pattern is null.
        flags = SimpleDateFormatPatternCore.Flags(false, false, false)
        flags = SimpleDateFormatPatternCore.scan(pattern!!)
    }
    fun compiledItems(): Array<Any> = patternItems ?: SimpleDateFormatPatternCore.compile(pattern!!).also { patternItems = it }
    fun applyPattern(value: String?) {
        pattern = value
        parsePattern()
        owner.setLocale(null, null)
        patternItems = null
        if (owner.calendar?.getType() == "japanese" && locale?.getLanguage() == "ja") {
            if (numberingOverride == "y=jpanyear" && !flags.hasHanYearChar) {
                numberFormatters = null; overrideMap = null; numberingOverride = null
            } else if (numberingOverride == null && flags.hasHanYearChar) {
                numberFormatters = HashMap(); overrideMap = HashMap()
                overrideMap!!['y'] = "jpanyear"
                val nf = owner.createNumberFormat(ULocale(locale!!.getBaseName() + "@numbers=jpanyear"))
                nf.setGroupingUsed(false)
                numbers.disableLocalFormat()
                numberFormatters!!["jpanyear"] = nf
                numberingOverride = "y=jpanyear"
            }
        }
    }
    fun applyLocalizedPattern(value: String?) {
        pattern = SimpleDateFormatPatternCore.translate(value!!, symbols!!.state.data.localPatternChars, PATTERN_CHARS)
        owner.setLocale(null, null)
        // Source does not rescan flags or reset the compiled pattern cache here.
    }
    fun toLocalizedPattern(): String = SimpleDateFormatPatternCore.translate(pattern!!, PATTERN_CHARS, symbols!!.state.data.localPatternChars)
    fun replaceSymbols(value: DateFormatSymbols?) { symbols = value!!.clone() }
    fun getSymbolsCopy(): DateFormatSymbols = symbols!!.clone()

    fun setCenturyStart(date: Date?) {
        defaultCenturyStart = date
        owner.calendar!!.setTime(date)
        defaultCenturyStartYear = owner.calendar!!.get(Calendar.YEAR)
    }
    fun initializeCentury(base: Long) {
        defaultCenturyBase = base
        val cal = owner.calendar!!.clone()
        cal.setTimeInMillis(base)
        cal.add(Calendar.YEAR, -80)
        defaultCenturyStart = cal.getTime()
        defaultCenturyStartYear = cal.get(Calendar.YEAR)
    }
    fun centuryStart(): Date? {
        if (defaultCenturyStart == null) initializeCentury(defaultCenturyBase)
        return defaultCenturyStart
    }
    fun centuryStartYear(): Int {
        if (defaultCenturyStart == null) initializeCentury(defaultCenturyBase)
        return defaultCenturyStartYear
    }

    fun initializeTimeZoneFormat(force: Boolean) = zoneLock.withLock {
        if (force || zoneFormat == null) {
            zoneFormat = TimeZoneFormat.getInstance(locale)
            val nf = owner.numberFormat
            val digits = when (nf) {
                is DecimalFormat -> nf.getDecimalFormatSymbols().getDigitStringsLocal().joinToString("")
                is DateNumberFormat -> nf.getDigits().concatToString()
                else -> null
            }
            if (digits != null && zoneFormat!!.getGMTOffsetDigits() != digits) {
                if (zoneFormat!!.isFrozen()) zoneFormat = zoneFormat!!.cloneAsThawed()
                zoneFormat!!.setGMTOffsetDigits(digits)
            }
        }
    }
    fun timeZoneFormat(): TimeZoneFormat {
        if (zoneFormat == null) initializeTimeZoneFormat(false)
        return zoneFormat!!
    }
    fun getFrozenTimeZoneFormat(): TimeZoneFormat = timeZoneFormat().freeze()
    fun setTimeZoneFormat(format: TimeZoneFormat?) {
        zoneFormat = if (format!!.isFrozen()) format else format.cloneAsThawed().freeze()
    }
    fun afterNumberFormatSet() {
        numbers.initialize(owner.numberFormat)
        initializeTimeZoneFormat(true)
        numberFormatters = null
        overrideMap = null
    }
    fun setFieldsNumberFormat(fields: String?, format: NumberFormat?) {
        format!!.setGroupingUsed(false)
        val key = owner.newFormatKey()
        if (numberFormatters == null) numberFormatters = HashMap()
        if (overrideMap == null) overrideMap = HashMap()
        for (ch in fields!!) {
            if (ch !in PATTERN_CHARS) throw IllegalArgumentException("Illegal field character '$ch' in setNumberFormat.")
            overrideMap!![ch] = key
            numberFormatters!![key] = format
        }
        numbers.disableLocalFormat()
    }
    fun numberFormat(ch: Char): NumberFormat? {
        if (overrideMap?.containsKey(ch) == true) {
            val key = overrideMap!![ch]!!.toString()
            return numberFormatters!![key]
        }
        return owner.numberFormat
    }
    fun initNumberFormatters(loc: ULocale?) {
        numberFormatters = HashMap()
        overrideMap = HashMap()
        processOverrideString(loc, numberingOverride)
    }
    fun processOverrideString(loc: ULocale?, value: String?) {
        if (value.isNullOrEmpty()) return
        var start = 0
        var more = true
        while (more) {
            val delimiter = value.indexOf(';', start)
            val end: Int
            if (delimiter == -1) { more = false; end = value.length } else end = delimiter
            if (start < 0 || end > value.length || start > end) throw IndexOutOfBoundsException()
            val current = value.substring(start, end)
            val equal = current.indexOf('=')
            val full = equal == -1
            val name: String
            if (full) name = current
            else {
                name = current.substring(equal + 1)
                if (current.isEmpty()) throw IndexOutOfBoundsException()
                overrideMap!![current[0]] = name
            }
            val nf = owner.createNumberFormat(ULocale(loc!!.getBaseName() + "@numbers=" + name))
            nf.setGroupingUsed(false)
            if (full) owner.setNumberFormatVirtual(nf) else numbers.disableLocalFormat()
            if (!full && !numberFormatters!!.containsKey(name)) numberFormatters!![name] = nf
            start = delimiter + 1
        }
    }
    fun copyStateFrom(other: SimpleDateFormatStateCore) {
        pattern = other.pattern; symbols = other.symbols!!.clone(); locale = other.locale
        numberingOverride = other.numberingOverride; useFastFormat = other.useFastFormat
        numberFormatters = other.numberFormatters; overrideMap = other.overrideMap
        defaultCenturyStart = other.defaultCenturyStart; defaultCenturyStartYear = other.defaultCenturyStartYear
        defaultCenturyBase = other.defaultCenturyBase; serialVersionOnStream = other.serialVersionOnStream
        flags = other.flags; patternItems = other.patternItems; zoneFormat = other.zoneFormat
        numbers.copyStateFrom(other.numbers)
    }
    companion object {
        const val PATTERN_CHARS = "GyMdkHmsSEDFwWahKzYeugAZvcLQqVUOXxrbB"
    }
}
