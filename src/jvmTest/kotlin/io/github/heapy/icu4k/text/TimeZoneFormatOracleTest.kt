@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.TimeZoneFormat as Source
import com.ibm.icu.util.TimeZone as SourceZone
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamePatternCore
import io.github.heapy.icu4k.util.*
import java.util.EnumSet
import kotlin.test.*

class TimeZoneFormatOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            else -> e.javaClass.simpleName + ":" + e.message
        }
    }
    private fun style(s: TimeZoneFormat.Style) = Source.Style.valueOf(s.name)
    @Test fun everyStyleUsesRealZoneHistoryAndFallback() {
        assertEquals(Source.Style.entries.map { it.name }, TimeZoneFormat.Style.entries.map { it.name })
        for (id in listOf("en_US", "fr_FR", "ru", "ar_EG", "ja", "zh_Hant", "en@numbers=mathbold")) {
            val a = Source.getInstance(SourceLocale(id)); val b = TimeZoneFormat.getInstance(ULocale(id))
            for (zone in listOf("America/New_York", "Europe/London", "Asia/Calcutta", "Asia/Kathmandu", "Europe/Kyiv", "America/Indiana/Knox", "Etc/GMT", "Etc/Unknown", "GMT+05:28:06")) {
                val az = SourceZone.getTimeZone(zone); val bz = TimeZone.getTimeZone(zone)
                for (date in listOf(-2208988800000L, 0L, 1710054000000L, 1730613600000L, 1767225600000L)) for (s in TimeZoneFormat.Style.entries) {
                    val at = com.ibm.icu.util.Output<Source.TimeType>(); val bt = Output<TimeZoneFormat.TimeType>()
                    assertEquals(outcome { a.format(style(s), az, date, at) }, outcome { b.format(s, bz, date, bt) }, "$id/$zone/$date/$s")
                    assertEquals(at.value?.name, bt.value?.name)
                }
            }
            assertEquals(a.gmtPattern, b.getGMTPattern()); assertEquals(a.gmtOffsetDigits, b.getGMTOffsetDigits())
            assertEquals(a.gmtZeroFormat, b.getGMTZeroFormat())
        }
    }
    @Test fun parseStylesOptionsPositionsAndTimeTypes() {
        for (id in listOf("en_US", "fr", "en_IL", "en_IN")) {
            val a = Source.getInstance(SourceLocale(id)); val b = TimeZoneFormat.getInstance(ULocale(id))
            for (text in listOf("Eastern Standard Time", "Eastern Daylight Time", "Eastern Time", "New York Time", "New York", "IST", "CST", "GMT+05:30", "UTC-530", "+052806", "-05:30", "Z", "America/New_York", "usnyc", "unk", "GMT", "xxGMT+1:30 suffix", "not-a-zone", "")) {
                for (s in TimeZoneFormat.Style.entries) for (options in listOf(null, emptySet(), setOf(TimeZoneFormat.ParseOption.ALL_STYLES), TimeZoneFormat.ParseOption.entries.toSet())) {
                    val ao = options?.let { EnumSet.noneOf(Source.ParseOption::class.java).also { set -> it.forEach { x -> set.add(Source.ParseOption.valueOf(x.name)) } } }
                    val start = if (text.startsWith("xx")) 2 else 0
                    val ap = java.text.ParsePosition(start); val bp = ParsePosition(start)
                    val at = com.ibm.icu.util.Output(Source.TimeType.DAYLIGHT); val bt = Output(TimeZoneFormat.TimeType.DAYLIGHT)
                    assertEquals(outcome { a.parse(style(s), text, ap, ao, at)?.id }, outcome { b.parse(s, text, bp, options, bt)?.getID() }, "$id/$text/$s/$options")
                    assertEquals(listOf(ap.index, ap.errorIndex, at.value?.name), listOf(bp.index, bp.errorIndex, bt.value?.name))
                }
            }
            for (text in listOf("GMT+1", "not-a-zone", "", null)) {
                assertEquals(outcome { a.parse(text)?.id }, outcome { b.parse(text)?.getID() })
                val ap = java.text.ParsePosition(0); val bp = ParsePosition(0)
                assertEquals(outcome { (a.parseObject(text, ap) as SourceZone?)?.id }, outcome { (b.parseObject(text, bp) as TimeZone?)?.getID() })
                assertEquals(listOf(ap.index, ap.errorIndex), listOf(bp.index, bp.errorIndex))
            }
        }
    }
    @Test fun frozenFactoriesMutatorsAndShallowClones() {
        val a = Source.getInstance(SourceLocale("en_US_TZFSTATE")); val b = TimeZoneFormat.getInstance(ULocale("en_US_TZFSTATE"))
        assertSame(b, TimeZoneFormat.getInstance(ULocale("en_US_TZFSTATE")))
        assertNotSame(TimeZoneFormat.getInstance(ULocale.US), TimeZoneFormat.getInstance(ULocale("en_US@numbers=latn")))
        assertSame(TimeZoneFormat.getInstance(ULocale.US), TimeZoneFormat.getInstance(java.util.Locale.US))
        assertEquals(outcome { Source.getInstance(null as SourceLocale?) }, outcome { TimeZoneFormat.getInstance(null as ULocale?) })
        assertEquals(outcome { a.setGMTPattern(null) }, outcome { b.setGMTPattern(null) })
        a.setDefaultParseOptions(EnumSet.allOf(Source.ParseOption::class.java)); b.setDefaultParseOptions(TimeZoneFormat.ParseOption.entries.toSet())
        assertEquals(a.defaultParseOptions.map { it.name }, b.getDefaultParseOptions()!!.map { it.name })
        val ac = a.cloneAsThawed(); val bc = b.cloneAsThawed()
        assertEquals(ac.isFrozen, bc.isFrozen()); assertEquals(a.clone().isFrozen, b.clone().isFrozen())
        ac.setGMTPattern("UTC'('{0}')'"); bc.setGMTPattern("UTC'('{0}')'")
        ac.setGMTOffsetPattern(Source.GMTOffsetPatternType.POSITIVE_HM, "+HH.mm"); bc.setGMTOffsetPattern(TimeZoneFormat.GMTOffsetPatternType.POSITIVE_HM, "+HH.mm")
        assertEquals(a.getGMTOffsetPattern(Source.GMTOffsetPatternType.POSITIVE_HM), b.getGMTOffsetPattern(TimeZoneFormat.GMTOffsetPatternType.POSITIVE_HM))
        assertEquals(a.formatOffsetLocalizedGMT(19_800_000), b.formatOffsetLocalizedGMT(19_800_000))
        for (value in listOf(null, "", "bad", "UTC{0}")) assertEquals(outcome { ac.setGMTPattern(value); ac.gmtPattern }, outcome { bc.setGMTPattern(value); bc.getGMTPattern() })
        for (value in listOf(null, "", "123", "0123456789", "٠١٢٣٤٥٦٧٨٩")) assertEquals(outcome { ac.setGMTOffsetDigits(value); ac.gmtOffsetDigits }, outcome { bc.setGMTOffsetDigits(value); bc.getGMTOffsetDigits() })
        for (offset in listOf(Int.MIN_VALUE, Int.MAX_VALUE, -86_400_000, -1, 0, 1, 1000, 19_845_000, 86_400_000)) {
            assertEquals(outcome { ac.formatOffsetLocalizedGMT(offset) }, outcome { bc.formatOffsetLocalizedGMT(offset) })
            for (utc in listOf(false, true)) for (short in listOf(false, true)) for (seconds in listOf(false, true)) {
                assertEquals(outcome { ac.formatOffsetISO8601Basic(offset, utc, short, seconds) }, outcome { bc.formatOffsetISO8601Basic(offset, utc, short, seconds) })
                assertEquals(outcome { ac.formatOffsetISO8601Extended(offset, utc, short, seconds) }, outcome { bc.formatOffsetISO8601Extended(offset, utc, short, seconds) })
            }
        }
    }
    @Test fun genericResourcePatternsUseJdkQuotesOnEveryLocale() {
        val files = mutableListOf<String>(); ICUData.visitResources(ICUData.ICU_ZONE_BASE_NAME + "/") { files.add(it) }
        val patterns = linkedSetOf("({0})", "{1} ({0})", "o''clock '{0}' [{0}]", "'{0}", "{2} {1} {0}")
        for (file in files.filter { it != "pool.res" && it != "res_index.res" }) {
            val bundle = io.github.heapy.icu4k.util.UResourceBundle.getBundleInstance(ICUData.ICU_ZONE_BASE_NAME, file.removeSuffix(".res")) as ICUResourceBundle
            for (key in listOf("regionFormat", "fallbackFormat")) {
                try { patterns.add(bundle.getStringWithFallback("zoneStrings/$key")) } catch (_: MissingResourceException) { }
            }
        }
        for (pattern in patterns) for (args in listOf(emptyArray(), arrayOf("France"), arrayOf("Paris", "Central European Time"), arrayOf(null, "O'Brien"))) {
            val a = java.text.MessageFormat(pattern).format(args)
            assertEquals(a, TimeZoneNamePatternCore(pattern).format(args), pattern)
        }
        assertTrue(patterns.size > 100)
    }
    @Test fun calendarFormatProtocolAndAttributedRanges() {
        val a = Source.getInstance(SourceLocale.US); val b = TimeZoneFormat.getInstance(ULocale.US)
        for (offset in listOf(-18_000_000, 0, 19_800_000)) for (field in listOf(0, com.ibm.icu.text.DateFormat.TIMEZONE_FIELD)) {
            val ap = java.text.FieldPosition(field); val bp = FormatFieldPosition(field)
            val ab = StringBuffer("prefix"); val bb = StringBuilder("prefix")
            a.format(com.ibm.icu.util.SimpleTimeZone(offset, "custom"), ab, ap); b.format(SimpleTimeZone(offset, "custom"), bb, bp)
            assertEquals(ab.toString(), bb.toString()); assertEquals(listOf(ap.beginIndex, ap.endIndex), listOf(bp.getBeginIndex(), bp.getEndIndex()))
            val ai = a.formatToCharacterIterator(com.ibm.icu.util.SimpleTimeZone(offset, "custom")); val bi = b.formatToCharacterIterator(SimpleTimeZone(offset, "custom"))
            assertEquals(ai.endIndex, bi.getEndIndex()); assertEquals(ai.attributes.values.map { it.toString().substringAfterLast('.') }, bi.getAttributes().values.map { it.toString().substringAfterLast('.') })
        }
        val ac = com.ibm.icu.util.GregorianCalendar(SourceZone.getTimeZone("America/New_York")); ac.timeInMillis = 1730613600000L
        val bc = GregorianCalendar(TimeZone.getTimeZone("America/New_York")); bc.setTimeInMillis(1730613600000L)
        assertEquals(a.format(ac), b.format(bc))
        for (input in listOf("bad", 5, null)) assertEquals(outcome { a.format(input) }, outcome { b.format(input) })
    }
}
