@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*

internal open class SimpleDateFormatFormatFixture(id: String, initialPattern: String) : SimpleDateFormatFormatCore.Owner {
    override var locale: ULocale? = ULocale(id)
    override var calendar: Calendar? = Calendar.getInstance(locale)
    override var symbols: DateFormatSymbols? = DateFormatSymbols(calendar, locale)
    override var numberFormat: NumberFormat? = null
    override var numberingOverride: String? = null
    override var useFastFormat = true
    var context: DisplayContext? = DisplayContext.CAPITALIZATION_NONE
    val core = SimpleDateFormatFormatCore(this)
    val numbers = SimpleDateFormatNumberCore()
    private var flags = SimpleDateFormatPatternCore.scan(initialPattern)
    override var pattern: String? = initialPattern
        set(value) { field = value; if (value != null) flags = SimpleDateFormatPatternCore.scan(value); core.resetPattern() }
    override val hasMinute get() = flags.hasMinute
    override val hasSecond get() = flags.hasSecond
    val trace = ArrayList<String>()
    var zoneFormatter: TimeZoneFormat = TimeZoneFormat.getInstance(locale)
    val overrides = HashMap<Char, NumberFormat?>()
    init { setNumber(NumberFormat.getInstance(locale)) }
    fun setNumber(value: NumberFormat?) {
        numberFormat = value?.clone()
        if (numberFormat != null) DateFormat.fixNumberFormatForDates(numberFormat!!)
        numbers.initialize(numberFormat)
        val nf = numberFormat
        val digits = when (nf) {
            is DateNumberFormat -> nf.getDigits().concatToString()
            is DecimalFormat -> nf.getDecimalFormatSymbols().getDigitStringsLocal().joinToString("")
            else -> null
        }
        zoneFormatter = TimeZoneFormat.getInstance(locale)
        if (digits != null && digits != zoneFormatter.getGMTOffsetDigits()) {
            zoneFormatter = zoneFormatter.cloneAsThawed()
            zoneFormatter.setGMTOffsetDigits(digits)
        }
    }
    fun setOverride(fields: String, value: NumberFormat?) {
        value!!.setGroupingUsed(false)
        for (ch in fields) overrides[ch] = value
        numbers.disableLocalFormat()
    }
    override fun capitalizationContext(): DisplayContext? = context
    override fun numberFormat(ch: Char): NumberFormat? { trace.add("number:$ch"); return if (overrides.containsKey(ch)) overrides[ch] else numberFormat }
    override fun timeZoneFormat(): TimeZoneFormat { trace.add("zone"); return zoneFormatter }
    override fun zeroPaddingNumber(nf: NumberFormat?, buf: StringBuilder?, value: Int, min: Int, max: Int) { trace.add("pad:$value:$min:$max"); numbers.format(nf, buf, value, min, max) }
    override fun subFormat(buf: StringBuilder?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?) {
        trace.add("field:$ch:$outputChar:$fieldNum")
        core.formatField(buf, ch, count, begin, fieldNum, context, pos, outputChar, cal)
    }
    override fun subFormat(ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String? {
        trace.add("slow:$ch:$outputChar:$fieldNum")
        return core.formatFieldString(ch, count, begin, fieldNum, context, pos, outputChar, cal)
    }
    override fun patternCharToDateFormatField(ch: Char): DateFormat.Field? { trace.add("attribute:$ch"); return SimpleDateFormatPatternCore.attribute(ch) }
    fun render(millis: Long, zone: String = "Etc/UTC"): String {
        calendar!!.setTimeZone(TimeZone.getTimeZone(zone))
        calendar!!.setTimeInMillis(millis)
        return core.format(calendar, StringBuilder(), FormatFieldPosition(-1)).toString()
    }
}
