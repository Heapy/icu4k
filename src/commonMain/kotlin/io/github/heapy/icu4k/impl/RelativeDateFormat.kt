// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*

/** ICU's relative-day style formatter. Relative-date parsing is unsupported in ICU4J. */
open class RelativeDateFormat : DateFormat {
    class URelativeString {
        @kotlin.jvm.JvmField var offset: Int
        @kotlin.jvm.JvmField var string: String?
        internal constructor(offset: Int, string: String?) { this.offset = offset; this.string = string }
        internal constructor(offset: String?, string: String?) : this(offset!!.toInt(), string)
    }
    private var fDateFormat: DateFormat? = null
    private var fTimeFormat: DateFormat? = null
    private var fCombinedFormat: MessageFormat? = null
    private var fDateTimeFormat: SimpleDateFormat? = null
    private var fDatePattern: String? = null
    private var fTimePattern: String? = null
    private var fDateStyle: Int = 0
    private var fTimeStyle: Int = 0
    private var fLocale: ULocale? = null
    @kotlin.jvm.Transient private var fDates: MutableList<URelativeString>? = null
    private var combinedFormatHasDateAtStart = false
    private var capitalizationInfoIsSet = false
    private var capitalizationOfRelativeUnitsForListOrMenu = false
    private var capitalizationOfRelativeUnitsForStandAlone = false
    @kotlin.jvm.Transient private var capitalizationBrkIter: BreakIterator? = null
    @kotlin.jvm.Transient private var datesLock = createCalendarCacheLock(this)

    constructor(timeStyle: Int, dateStyle: Int, locale: ULocale?, cal: Calendar?) : super() {
        calendar = cal
        fLocale = locale; fTimeStyle = timeStyle; fDateStyle = dateStyle
        if (fDateStyle != NONE) {
            val date = getDateInstance(fDateStyle and RELATIVE.inv(), locale)
            if (date is SimpleDateFormat) fDateTimeFormat = date
            else throw IllegalArgumentException("Can't create SimpleDateFormat for date style")
            fDatePattern = fDateTimeFormat!!.toPattern()
            if (fTimeStyle != NONE) {
                val time = getTimeInstance(fTimeStyle and RELATIVE.inv(), locale)
                if (time is SimpleDateFormat) fTimePattern = time.toPattern()
            }
        } else {
            val time = getTimeInstance(fTimeStyle and RELATIVE.inv(), locale)
            if (time is SimpleDateFormat) fDateTimeFormat = time
            else throw IllegalArgumentException("Can't create SimpleDateFormat for time style")
            fTimePattern = fDateTimeFormat!!.toPattern()
        }
        if (calendar == null) calendar = Calendar.getInstance(fLocale)
        loadDates()
        initializeCombinedFormat(calendar!!, fLocale)
    }
    protected constructor(other: RelativeDateFormat, @Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super(other, Unit) {
        fDateFormat = other.fDateFormat; fTimeFormat = other.fTimeFormat
        fCombinedFormat = other.fCombinedFormat; fDateTimeFormat = other.fDateTimeFormat
        fDatePattern = other.fDatePattern; fTimePattern = other.fTimePattern
        fDateStyle = other.fDateStyle; fTimeStyle = other.fTimeStyle; fLocale = other.fLocale
        fDates = other.fDates; combinedFormatHasDateAtStart = other.combinedFormatHasDateAtStart
        capitalizationInfoIsSet = other.capitalizationInfoIsSet
        capitalizationOfRelativeUnitsForListOrMenu = other.capitalizationOfRelativeUnitsForListOrMenu
        capitalizationOfRelativeUnitsForStandAlone = other.capitalizationOfRelativeUnitsForStandAlone
        capitalizationBrkIter = other.capitalizationBrkIter
    }
    public override fun clone(): DateFormat {
        val copy = super.clone() as RelativeDateFormat
        // Object.clone copies the JVM monitor adapter, whose owner must be the new object.
        copy.datesLock = createCalendarCacheLock(copy)
        return copy
    }
    protected override fun cloneNative(): DateFormat = RelativeDateFormat(this, Unit)

    override fun format(calendar: Calendar?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        var relative: String? = null
        val context = getContext(DisplayContext.Type.CAPITALIZATION)
        if (fDateStyle != NONE) {
            val until = calendar!!
            val now = until.clone()
            val nowDate = Date()
            now.clear(); now.setTime(nowDate)
            relative = getStringForDay(until.get(Calendar.JULIAN_DAY) - now.get(Calendar.JULIAN_DAY))
        }
        val formatter = fDateTimeFormat
        if (formatter != null) {
            if (relative != null && fDatePattern != null && (fTimePattern == null || fCombinedFormat == null || combinedFormatHasDateAtStart)) {
                if (relative.isNotEmpty() && UCharacter.isLowerCase(UTF16.charAt(relative, 0)) && needsTitlecase(context)) {
                    if (capitalizationBrkIter == null) capitalizationBrkIter = BreakIterator.getSentenceInstance(fLocale)
                    relative = UCharacter.toTitleCase(fLocale, relative, capitalizationBrkIter, UCharacter.TITLECASE_NO_LOWERCASE or UCharacter.TITLECASE_NO_BREAK_ADJUSTMENT)
                }
                formatter.setContext(DisplayContext.CAPITALIZATION_NONE)
            } else formatter.setContext(context)
        }
        if (formatter != null && (fDatePattern != null || fTimePattern != null)) {
            if (fDatePattern == null) {
                formatter.applyPattern(fTimePattern)
                formatter.format(calendar, output, position)
            } else if (fTimePattern == null) {
                if (relative != null) output!!.append(relative)
                else { formatter.applyPattern(fDatePattern); formatter.format(calendar, output, position) }
            } else {
                val datePattern = if (relative != null) "'" + relative.replace("'", "''") + "'" else fDatePattern
                val combined = StringBuilder()
                fCombinedFormat!!.format(arrayOf(fTimePattern, datePattern), combined, FormatFieldPosition(0))
                formatter.applyPattern(combined.toString())
                formatter.format(calendar, output, position)
            }
        } else if (fDateFormat != null) {
            if (relative != null) output!!.append(relative)
            else fDateFormat!!.format(calendar, output, position)
        }
        return output as StringBuilder
    }
    override fun parse(text: String?, calendar: Calendar?, position: ParsePosition?): Unit = throw UnsupportedOperationException("Relative Date parse is not implemented yet")
    override fun setContext(context: DisplayContext?) {
        super.setContext(context)
        if (!capitalizationInfoIsSet && (context == DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU || context == DisplayContext.CAPITALIZATION_FOR_STANDALONE)) {
            initCapitalizationContextInfo(fLocale)
            capitalizationInfoIsSet = true
        }
        if (capitalizationBrkIter == null && needsTitlecase(context)) capitalizationBrkIter = BreakIterator.getSentenceInstance(fLocale)
    }
    private fun needsTitlecase(context: DisplayContext?): Boolean = context == DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE ||
        (context == DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU && capitalizationOfRelativeUnitsForListOrMenu) ||
        (context == DisplayContext.CAPITALIZATION_FOR_STANDALONE && capitalizationOfRelativeUnitsForStandAlone)
    private fun getStringForDay(day: Int): String? {
        if (fDates == null) loadDates()
        for (item in fDates!!) if (item.offset == day) return item.string
        return null
    }
    private inner class RelDateFmtDataSink : UResource.Sink() {
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            if (value.getType() == ICUResourceBundle.ALIAS) return
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                val offset = try { key.toString().toInt() } catch (_: NumberFormatException) { return }
                if (getStringForDay(offset) == null) fDates!!.add(URelativeString(offset, value.getString()))
            }
        }
    }
    private fun loadDates() = datesLock.withLock {
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, fLocale) as ICUResourceBundle
        fDates = ArrayList()
        bundle.getAllItemsWithFallback("fields/day/relative", RelDateFmtDataSink())
    }
    private fun initCapitalizationContextInfo(locale: ULocale?) {
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
        try {
            val flags = bundle.getWithFallback("contextTransforms/relative").getIntVector()
            if (flags.size >= 2) {
                capitalizationOfRelativeUnitsForListOrMenu = flags[0] != 0
                capitalizationOfRelativeUnitsForStandAlone = flags[1] != 0
            }
        } catch (_: MissingResourceException) { }
    }
    private fun initializeCombinedFormat(cal: Calendar, locale: ULocale?) {
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
        var patterns = bundle.findWithFallback("calendar/" + cal.getType() + "/DateTimePatterns")
        if (patterns == null && cal.getType() != "gregorian") patterns = bundle.findWithFallback("calendar/gregorian/DateTimePatterns")
        val pattern = if (patterns == null || patterns.getSize() < 9) "{1} {0}" else {
            var index = 8
            if (patterns.getSize() >= 13) {
                if (fDateStyle in FULL..SHORT) index += fDateStyle + 1
                else if (fDateStyle in RELATIVE_FULL..RELATIVE_SHORT) index += fDateStyle + 1 - RELATIVE
            }
            if (patterns.get(index).getType() == UResourceBundle.ARRAY) patterns.get(index).getString(0) else patterns.getString(index)
        }
        combinedFormatHasDateAtStart = pattern.startsWith("{1}")
        fCombinedFormat = MessageFormat(pattern, locale)
    }
    private fun readObject(input: PluralObjectInputStream) {
        relativeDateFormatRead(input)
        datesLock = createCalendarCacheLock(this)
    }
    companion object { private const val serialVersionUID = 1131984966440549435L }
}
internal expect fun relativeDateFormatRead(input: PluralObjectInputStream)
