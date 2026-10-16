package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.util.TimeZone as SourceZone
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class TimeZoneDisplayNameCoreOracleTest {
    private fun core(locale: ULocale): TimeZoneDisplayNameCore<SourceZone> {
        val names = TimeZoneNamesDataCore(locale, { com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(SourceZone.getDefault()) })
        val genericNames = GenericNamesOracleSupport.names(names)
        val generic = TimeZoneGenericNamesCore(locale, genericNames, GenericNamesOracleSupport.Owner(genericNames))
        val localized = LocalizedGMTOffsetCore(locale)
        val namesService = object : TimeZoneFormatDisplayCore.Names, TimeZoneDisplayNameCore.Names {
            override fun getDisplayName(id: String?, type: NameType, date: Long) = names.getDisplayName(id, type, date)
            override fun getExemplarLocationName(id: String?) = names.getExemplarLocationName(id)
        }
        val format = TimeZoneFormatDisplayCore(localized, object : TimeZoneFormatDisplayCore.OwnerQueries<SourceZone> {
            override fun getID(zone: SourceZone?) = zone!!.id
            override fun getShortID(zone: SourceZone?) = com.ibm.icu.impl.ZoneMeta.getShortID(zone)
            override fun getCanonicalCLDRID(zone: SourceZone?) = com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(zone)
            override fun inDaylightTime(zone: SourceZone?, date: Long) = zone!!.inDaylightTime(java.util.Date(date))
            override fun getOffset(zone: SourceZone?, date: Long, local: Boolean, offsets: IntArray) = zone!!.getOffset(date, local, offsets)
            override fun getNames() = namesService
            override fun getGenericNames() = object : TimeZoneFormatDisplayCore.GenericNames<SourceZone> {
                override fun getGenericLocationName(id: String?) = generic.getGenericLocationName(id)
                override fun getDisplayName(zone: SourceZone?, type: GenericNameType, date: Long) = generic.getDisplayName(zone, type, date)
            }
        })
        val formatService = object : TimeZoneDisplayNameCore.Format<SourceZone> {
            override fun format(style: Style, zone: SourceZone, date: Long, timeType: Output<TimeType>) = format.format(style, zone, date, timeType)
            override fun formatOffsetLocalizedGMT(offset: Int) = localized.formatOffsetLocalizedGMT(offset)
            override fun formatOffsetShortLocalizedGMT(offset: Int) = localized.formatOffsetShortLocalizedGMT(offset)
            override fun formatOffsetISO8601Basic(offset: Int, useUtcIndicator: Boolean, isShort: Boolean, ignoreSeconds: Boolean) =
                TimeZoneOffsetCore.formatOffsetISO8601Basic(offset, useUtcIndicator, isShort, ignoreSeconds)
        }
        return TimeZoneDisplayNameCore(object : TimeZoneDisplayNameCore.Owner<SourceZone> {
            override fun getFormat(locale: ULocale) = formatService
            override fun getNames(locale: ULocale) = namesService
            override fun currentTimeMillis() = System.currentTimeMillis()
            override fun getRawOffset(zone: SourceZone) = zone.rawOffset
            override fun getDSTSavings(zone: SourceZone) = zone.dstSavings
            override fun useDaylightTime(zone: SourceZone) = zone.useDaylightTime()
            override fun getCanonicalCLDRID(zone: SourceZone) = com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(zone)
        }, SourceZone::class.java.desiredAssertionStatus())
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) {
            is VirtualMachineError -> throw e
            is IndexOutOfBoundsException -> "bounds"
            else -> e.javaClass.simpleName + ":" + e.message
        }
    }

    @Test fun everyZoneStyleRequestedSeasonAndLocale() {
        for (localeID in listOf("root", "en_US", "en_GB", "fr", "ar", "zh", "he")) {
            val locale = ULocale(localeID)
            val sourceLocale = com.ibm.icu.util.ULocale(localeID)
            val core = core(locale)
            for (id in SourceZone.getAvailableIDs().toList() + listOf("Etc/Unknown", "GMT+05:30:45", "GMT-00:00:01", "unknown")) {
                val zone = SourceZone.getTimeZone(id)
                for (daylight in listOf(false, true)) for (style in 0..7) {
                    assertEquals(zone.getDisplayName(daylight, style, sourceLocale), core.getDisplayName(zone, daylight, style, locale), "$localeID/$id/$daylight/$style")
                }
            }
        }
    }

    @Test fun overflowInvalidStylesAndNullLocalePrecedence() {
        val locale = ULocale("en")
        val core = core(locale)
        for (raw in listOf(Int.MIN_VALUE, Int.MAX_VALUE, -86400000, -86399999, -999, 0, 999, 86399999, 86400000)) {
            val zone = com.ibm.icu.util.SimpleTimeZone(raw, "custom")
            for (style in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, Int.MAX_VALUE)) for (daylight in listOf(false, true)) {
                for (loc in listOf(locale, null)) {
                    assertEquals(outcome { zone.getDisplayName(daylight, style, loc?.let { com.ibm.icu.util.ULocale(it.getName()) }) },
                        outcome { core.getDisplayName(zone, daylight, style, loc) }, "$raw/$style/$daylight/$loc")
                }
            }
        }
    }
}
