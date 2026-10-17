// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.timezone.*
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.Volatile

/** Locale-sensitive named and numeric time-zone formatting and prefix parsing. */
open class TimeZoneFormat : UFormat, Freezable<TimeZoneFormat> {
    enum class Style(internal val flag: Int) {
        GENERIC_LOCATION(1), GENERIC_LONG(2), GENERIC_SHORT(4), SPECIFIC_LONG(8), SPECIFIC_SHORT(16),
        LOCALIZED_GMT(32), LOCALIZED_GMT_SHORT(64), ISO_BASIC_SHORT(128), ISO_BASIC_LOCAL_SHORT(256),
        ISO_BASIC_FIXED(128), ISO_BASIC_LOCAL_FIXED(256), ISO_BASIC_FULL(128), ISO_BASIC_LOCAL_FULL(256),
        ISO_EXTENDED_FIXED(128), ISO_EXTENDED_LOCAL_FIXED(256), ISO_EXTENDED_FULL(128), ISO_EXTENDED_LOCAL_FULL(256),
        ZONE_ID(512), ZONE_ID_SHORT(1024), EXEMPLAR_LOCATION(2048)
    }
    enum class GMTOffsetPatternType(internal val defaultPattern: String, internal val required: String, internal val positive: Boolean) {
        POSITIVE_HM("+H:mm", "Hm", true), POSITIVE_HMS("+H:mm:ss", "Hms", true),
        NEGATIVE_HM("-H:mm", "Hm", false), NEGATIVE_HMS("-H:mm:ss", "Hms", false),
        POSITIVE_H("+H", "H", true), NEGATIVE_H("-H", "H", false)
    }
    enum class TimeType { UNKNOWN, STANDARD, DAYLIGHT }
    enum class ParseOption { ALL_STYLES, TZ_DATABASE_ABBREVIATIONS }

    @kotlin.jvm.Transient internal var locale: ULocale? = null
    @kotlin.jvm.Transient internal var names: TimeZoneNames? = null
    @kotlin.jvm.Transient internal var localized: LocalizedGMTOffsetCore? = null
    @kotlin.jvm.Transient internal var parseAllStyles = false
    @kotlin.jvm.Transient internal var parseTZDBNames = false
    @kotlin.jvm.Transient @Volatile private var frozen = false
    @kotlin.jvm.Transient @Volatile private var generic: TimeZoneGenericNamesCore<TimeZone>? = null
    @kotlin.jvm.Transient @Volatile private var tzdb: TimeZoneNames? = null
    @kotlin.jvm.Transient private var lock = createAvailabilityLock()

    protected constructor(locale: ULocale?) : super() {
        this.locale = locale
        names = TimeZoneNames.getInstance(locale)
        localized = LocalizedGMTOffsetCore(locale)
    }
    protected constructor(other: TimeZoneFormat, @Suppress("UNUSED_PARAMETER") copyMarker: Unit) : super() {
        locale = other.locale; names = other.names; localized = other.localized
        parseAllStyles = other.parseAllStyles; parseTZDBNames = other.parseTZDBNames
        frozen = other.frozen; generic = other.generic; tzdb = other.tzdb
        other.copyLocaleTo(this)
    }
    internal fun genericNames(): TimeZoneGenericNamesCore<TimeZone> = generic ?: lock.withLock {
        generic ?: TimeZoneFormatOwners.cachedGeneric(locale!!).also { generic = it }
    }
    internal fun tzdbNames(): TimeZoneNames = tzdb ?: lock.withLock {
        tzdb ?: TimeZoneNames.getTZDBInstance(locale).also { tzdb = it }
    }
    open fun getTimeZoneNames(): TimeZoneNames? = names
    open fun setTimeZoneNames(tznames: TimeZoneNames?): TimeZoneFormat {
        checkMutable()
        names = tznames
        generic = TimeZoneFormatOwners.newGeneric(locale, tznames)
        return this
    }
    private fun checkMutable() { if (isFrozen()) throw UnsupportedOperationException("Attempt to modify frozen object") }
    open fun getGMTPattern(): String = localized!!.getGMTPattern()
    open fun setGMTPattern(pattern: String?): TimeZoneFormat { checkMutable(); localized!!.setGMTPattern(pattern); return this }
    open fun getGMTOffsetPattern(type: GMTOffsetPatternType?): String = localized!!.getGMTOffsetPattern(type)
    open fun setGMTOffsetPattern(type: GMTOffsetPatternType?, pattern: String?): TimeZoneFormat {
        checkMutable(); localized!!.setGMTOffsetPattern(type, pattern); return this
    }
    open fun getGMTOffsetDigits(): String = localized!!.getGMTOffsetDigits()
    open fun setGMTOffsetDigits(digits: String?): TimeZoneFormat { checkMutable(); localized!!.setGMTOffsetDigits(digits); return this }
    open fun getGMTZeroFormat(): String = localized!!.getGMTZeroFormat()
    open fun setGMTZeroFormat(gmtZeroFormat: String?): TimeZoneFormat { checkMutable(); localized!!.setGMTZeroFormat(gmtZeroFormat); return this }
    // The source intentionally does not check frozen state for parse options.
    open fun setDefaultParseOptions(options: Set<ParseOption>?): TimeZoneFormat {
        parseAllStyles = options!!.contains(ParseOption.ALL_STYLES)
        parseTZDBNames = options.contains(ParseOption.TZ_DATABASE_ABBREVIATIONS)
        return this
    }
    open fun getDefaultParseOptions(): MutableSet<ParseOption>? = timeZoneParseOptions(parseAllStyles, parseTZDBNames)
    fun formatOffsetISO8601Basic(offset: Int, useUtcIndicator: Boolean, isShort: Boolean, ignoreSeconds: Boolean): String =
        TimeZoneOffsetCore.formatOffsetISO8601Basic(offset, useUtcIndicator, isShort, ignoreSeconds)
    fun formatOffsetISO8601Extended(offset: Int, useUtcIndicator: Boolean, isShort: Boolean, ignoreSeconds: Boolean): String =
        TimeZoneOffsetCore.formatOffsetISO8601Extended(offset, useUtcIndicator, isShort, ignoreSeconds)
    open fun formatOffsetLocalizedGMT(offset: Int): String? = localized!!.formatOffsetLocalizedGMT(offset)
    open fun formatOffsetShortLocalizedGMT(offset: Int): String? = localized!!.formatOffsetShortLocalizedGMT(offset)
    fun format(style: Style?, tz: TimeZone?, date: Long): String? = format(style, tz, date, null)
    open fun format(style: Style?, tz: TimeZone?, date: Long, timeType: Output<TimeType>?): String? =
        TimeZoneFormatDisplayCore(localized!!, displayOwner()).format(style, tz, date, timeType)
    fun parseOffsetISO8601(text: String?, pos: ParsePosition?): Int = TimeZoneOffsetCore.parseOffsetISO8601(text, pos)
    open fun parseOffsetLocalizedGMT(text: String?, pos: ParsePosition?): Int = localized!!.parseOffsetLocalizedGMT(text, pos)
    open fun parseOffsetShortLocalizedGMT(text: String?, pos: ParsePosition?): Int = localized!!.parseOffsetLocalizedGMT(text, pos, true)
    open fun parse(style: Style?, text: String?, pos: ParsePosition?, options: Set<ParseOption>?, timeType: Output<TimeType>?): TimeZone? =
        TimeZoneFormatParseCore(locale!!, localized!!, parseOwner()).parse(style, text, pos, options, timeType)
    open fun parse(style: Style?, text: String?, pos: ParsePosition?, timeType: Output<TimeType>?): TimeZone? =
        parse(style, text, pos, null, timeType)
    fun parse(text: String?, pos: ParsePosition?): TimeZone? = parse(Style.GENERIC_LOCATION, text, pos, setOf(ParseOption.ALL_STYLES), null)
    fun parse(text: String?): TimeZone? {
        val pos = ParsePosition(0)
        val result = parse(text, pos)
        if (pos.errorIndex >= 0) throw ParseException("Unparseable time zone: \"$text\"", 0)
        if (timeZoneOffsetAssertionsEnabled() && result == null) throw AssertionError()
        return result
    }
    override fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        var date = Date().getTime()
        val zone = when (input) {
            is TimeZone -> input
            is Calendar -> { val tz = input.getTimeZone(); date = input.getTimeInMillis(); tz }
            else -> throw IllegalArgumentException("Cannot format given Object (" + dateFormatClassName(input!!) + ") as a time zone")
        }
        if (timeZoneOffsetAssertionsEnabled() && zone == null) throw AssertionError()
        val result = formatOffsetLocalizedGMT(zone!!.getOffset(date))
        output!!.append(result)
        if (position!!.getFieldAttribute() === DateFormat.Field.TIME_ZONE || position.getField() == DateFormat.TIMEZONE_FIELD) {
            position.setBeginIndex(0); position.setEndIndex(result!!.length)
        }
        return output
    }
    override fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator {
        val result = format(input, StringBuilder(), FormatFieldPosition(0)).toString()
        if (result.isEmpty()) throw IllegalArgumentException("Can't add attribute to 0-length text")
        return attributedCharacterIterator(result, listOf(FormatAttributeRange(DateFormat.Field.TIME_ZONE, DateFormat.Field.TIME_ZONE, 0, result.length)))
    }
    override fun parseObject(source: String?, position: ParsePosition?): Any? = parse(source, position)
    override fun isFrozen(): Boolean = frozen
    override fun freeze(): TimeZoneFormat { frozen = true; return this }
    private fun copy(): TimeZoneFormat {
        val copy = cloneHost() as? TimeZoneFormat ?: cloneNative()
        copy.localized = localized!!.clone()
        copy.lock = createAvailabilityLock()
        return copy
    }
    protected open fun cloneNative(): TimeZoneFormat = TimeZoneFormat(this, Unit)
    override fun clone(): TimeZoneFormat = copy()
    override fun cloneAsThawed(): TimeZoneFormat = copy().also { it.frozen = false }
    internal fun restoreDerived(pattern: String, offsets: Array<String>, digits: Array<String>, zero: String) {
        lock = createAvailabilityLock()
        frozen = false; parseTZDBNames = false; tzdb = null
        if (names is io.github.heapy.icu4k.impl.TimeZoneNamesImpl) { names = TimeZoneNames.getInstance(locale); generic = null }
        else generic = TimeZoneFormatOwners.newGeneric(locale, names)
        localized = LocalizedGMTOffsetCore.fromSerialized(pattern, offsets, digits, zero)
    }
    private fun writeObject(output: PluralObjectOutputStream) = timeZoneFormatWrite(output, this)
    private fun readObject(input: PluralObjectInputStream) = timeZoneFormatRead(input, this)
    companion object {
        private const val serialVersionUID = 2281246852693575022L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = timeZoneFormatSerialFields()
        private val cache = object : SoftCache<ULocale, TimeZoneFormat, ULocale>() {
            override fun createInstance(key: ULocale, data: ULocale): TimeZoneFormat = TimeZoneFormat(data).freeze()
        }
        @kotlin.jvm.JvmStatic
        fun getInstance(locale: ULocale?): TimeZoneFormat {
            if (locale == null) throw NullPointerException("locale is null")
            return cache.getInstance(locale, locale)!!
        }
    }
}
internal expect fun timeZoneFormatSerialFields(): Array<RbnfObjectStreamField>
internal expect fun timeZoneFormatWrite(output: PluralObjectOutputStream, owner: TimeZoneFormat)
internal expect fun timeZoneFormatRead(input: PluralObjectInputStream, owner: TimeZoneFormat)

internal expect fun timeZoneParseOptions(all: Boolean, tzdb: Boolean): MutableSet<TimeZoneFormat.ParseOption>
