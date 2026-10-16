package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.Companion.SHORT
import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.Companion.LONG
import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.Companion.SHORT_GENERIC
import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.Companion.LONG_GENERIC
import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.Companion.SHORT_GMT
import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.Companion.LONG_GMT
import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.Companion.SHORT_COMMONLY_USED
import io.github.heapy.icu4k.impl.timezone.TimeZoneDisplayNameCore.Companion.GENERIC_LOCATION
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.Style
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.TimeType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class TimeZoneDisplayNameCoreTest {
    private class Fixture : Owner<Unit>, Format<Unit>, Names {
        val trace = ArrayList<String>()
        var type: TimeType? = TimeType.UNKNOWN
        var usesDst = false
        var raw = 100
        var dst = 20
        var name: String? = "name"
        var formatted: String? = "generic"
        var missingFormat = false
        var missingNames = false
        val core = TimeZoneDisplayNameCore(this, false)
        fun display(daylight: Boolean, style: Int) = core.getDisplayName(Unit, daylight, style, ULocale.ENGLISH)
        override fun getFormat(locale: ULocale): Format<Unit>? { trace.add("format"); return if (missingFormat) null else this }
        override fun getNames(locale: ULocale): Names? { trace.add("names"); return if (missingNames) null else this }
        override fun currentTimeMillis(): Long { trace.add("clock"); return 123 }
        override fun getRawOffset(zone: Unit): Int { trace.add("raw"); return raw }
        override fun getDSTSavings(zone: Unit): Int { trace.add("dst"); return dst }
        override fun useDaylightTime(zone: Unit): Boolean { trace.add("uses"); return usesDst }
        override fun getCanonicalCLDRID(zone: Unit): String { trace.add("canonical"); return "canonical" }
        override fun getDisplayName(id: String?, type: NameType, date: Long): String? {
            trace.add("name:$id:$type:$date"); return name
        }
        override fun format(style: Style, zone: Unit, date: Long, timeType: Output<TimeType>): String? {
            trace.add("generic:$style:$date:${timeType.value}"); timeType.value = type; return formatted
        }
        override fun formatOffsetLocalizedGMT(offset: Int): String { trace.add("long:$offset"); return "long:$offset" }
        override fun formatOffsetShortLocalizedGMT(offset: Int): String { trace.add("short:$offset"); return "short:$offset" }
        override fun formatOffsetISO8601Basic(offset: Int, useUtcIndicator: Boolean, isShort: Boolean, ignoreSeconds: Boolean): String {
            trace.add("iso:$offset:$useUtcIndicator:$isShort:$ignoreSeconds"); return "iso:$offset"
        }
    }

    @Test fun genericFallbackUsesRequestedSeasonWithoutUseDaylightQuery() {
        for ((style, formatStyle) in listOf(SHORT_GENERIC to Style.GENERIC_SHORT, LONG_GENERIC to Style.GENERIC_LONG, GENERIC_LOCATION to Style.GENERIC_LOCATION)) {
            for (type in TimeType.entries + listOf(null)) for (daylight in listOf(false, true)) {
                val f = Fixture(); f.type = type
                val mismatch = (daylight && type == TimeType.STANDARD) || (!daylight && type == TimeType.DAYLIGHT)
                val prefix = if (style == SHORT_GENERIC) "short" else "long"
                val offset = if (daylight) 120 else 100
                assertEquals(if (mismatch) "$prefix:$offset" else "generic", f.display(daylight, style))
                assertEquals(listOf("format", "clock", "generic:$formatStyle:123:UNKNOWN") +
                    if (mismatch) listOf("raw") + (if (daylight) listOf("dst") else emptyList()) + "$prefix:$offset" else emptyList(), f.trace)
            }
        }
    }

    @Test fun specificAndNumericStylesRetainCallbackOrderAndOverflow() {
        for (style in listOf(SHORT, LONG, SHORT_COMMONLY_USED, SHORT_GMT, LONG_GMT)) {
            for (daylight in listOf(false, true)) for (uses in listOf(false, true)) {
                val f = Fixture(); f.usesDst = uses; f.name = null; f.raw = Int.MAX_VALUE; f.dst = 1
                val offset = if (daylight && uses) Int.MIN_VALUE else Int.MAX_VALUE
                val prefix = when (style) { SHORT_GMT -> "iso"; LONG, LONG_GMT -> "long"; else -> "short" }
                assertEquals("$prefix:$offset", f.display(daylight, style))
                val named = style == SHORT || style == LONG || style == SHORT_COMMONLY_USED
                val nameType = if (style == LONG) {
                    if (daylight) NameType.LONG_DAYLIGHT else NameType.LONG_STANDARD
                } else if (daylight) NameType.SHORT_DAYLIGHT else NameType.SHORT_STANDARD
                val expected = (if (named) listOf("clock", "names", "canonical", "name:canonical:$nameType:123") else emptyList()) +
                    "format" + (if (daylight) listOf("uses") else emptyList()) + "raw" +
                    (if (daylight && uses) listOf("dst") else emptyList()) +
                    if (style == SHORT_GMT) "iso:$offset:false:false:false" else "$prefix:$offset"
                assertEquals(expected, f.trace)
            }
        }
        val named = Fixture()
        assertEquals("name", named.display(true, LONG))
        assertEquals(listOf("clock", "names", "canonical", "name:canonical:LONG_DAYLIGHT:123"), named.trace)
    }

    @Test fun validationNullReceiversAndAssertionPolicy() {
        val f = Fixture()
        assertEquals("Illegal style: -1", assertFailsWith<IllegalArgumentException> { f.core.getDisplayName(Unit, false, -1, null) }.message)
        assertEquals("locale is null", assertFailsWith<NullPointerException> { f.core.getDisplayName(Unit, false, LONG, null) }.message)
        assertTrue(f.trace.isEmpty())
        f.missingNames = true
        assertFailsWith<NullPointerException> { f.display(false, LONG) }
        assertEquals(listOf("clock", "names", "canonical"), f.trace)
        f.trace.clear(); f.missingFormat = true
        assertFailsWith<NullPointerException> { f.display(true, LONG_GMT) }
        assertEquals(listOf("format", "uses", "raw"), f.trace)
        f.trace.clear()
        assertFailsWith<NullPointerException> { f.display(false, LONG_GENERIC) }
        assertEquals(listOf("format", "clock"), f.trace)
        f.missingFormat = false; f.formatted = null
        assertNull(f.display(false, LONG_GENERIC))
        assertFailsWith<AssertionError> { TimeZoneDisplayNameCore(f, true).getDisplayName(Unit, false, LONG_GENERIC, ULocale.ENGLISH) }
    }
}
