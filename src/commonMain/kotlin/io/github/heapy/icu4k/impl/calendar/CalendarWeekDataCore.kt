// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.UResourceBundle

/** Calendar.WeekData's immutable value contract, without normalizing its inputs. */
internal class CalendarWeekData(
    val firstDayOfWeek: Int,
    val minimalDaysInFirstWeek: Int,
    val weekendOnset: Int,
    val weekendOnsetMillis: Int,
    val weekendCease: Int,
    val weekendCeaseMillis: Int,
) {
    override fun hashCode(): Int = ((((firstDayOfWeek * 37 + minimalDaysInFirstWeek) * 37 + weekendOnset) * 37 +
        weekendOnsetMillis) * 37 + weekendCease) * 37 + weekendCeaseMillis
    override fun equals(other: Any?): Boolean = this === other || other is CalendarWeekData &&
        firstDayOfWeek == other.firstDayOfWeek && minimalDaysInFirstWeek == other.minimalDaysInFirstWeek &&
        weekendOnset == other.weekendOnset && weekendOnsetMillis == other.weekendOnsetMillis &&
        weekendCease == other.weekendCease && weekendCeaseMillis == other.weekendCeaseMillis
    override fun toString(): String = "{$firstDayOfWeek, $minimalDaysInFirstWeek, $weekendOnset, $weekendOnsetMillis, $weekendCease, $weekendCeaseMillis}"
}

/** The public region lookup deliberately creates fresh values; initialization uses the soft cache. */
internal object CalendarWeekDataCore {
    private val cache = object : SoftCache<String, CalendarWeekData, String>() {
        override fun createInstance(key: String, data: String): CalendarWeekData = getWeekDataForRegion(key)
    }
    fun cachedForRegion(region: String?): CalendarWeekData {
        val key = region ?: "001"
        return cache.getInstance(key, key)!!
    }
    fun getWeekDataForRegion(region: String?): CalendarWeekData {
        val key = region ?: "001"
        val weeks = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "supplementalData")["weekData"]
        val bundle = try { weeks[key] } catch (e: MissingResourceException) {
            if (key != "001") weeks["001"] else throw e
        }
        val values = bundle.getIntVector()
        return CalendarWeekData(values[0], values[1], values[2], values[3], values[4], values[5])
    }
}
