// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.impl.timezone.TimeZoneMetadataCore
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.util.ULocale

/** Lazy DateFormatSymbols zone table, with clock and default zone supplied by its owner. */
internal class DateFormatSymbolsZoneStringsCore(
    private val currentTimeMillis: () -> Long,
    private val names: (ULocale?) -> Names,
    private val availableIDs: () -> Array<String> = TimeZoneMetadataCore::getAvailableIDs,
    private val canonicalID: (String) -> String? = { TimeZoneMetadataCore.getCanonicalID(it) },
) {
    interface Names {
        fun loadAllDisplayNames()
        fun getDisplayNames(id: String, types: Array<NameType>, date: Long, dest: Array<String?>, offset: Int)
    }

    fun getZoneStrings(state: DateFormatSymbolsStateCore): Array<Array<String?>?> {
        if (state.storedZoneStrings != null) return state.copyZoneStrings()
        val ids = availableIDs()
        val provider = names(state.data.validLocale)
        provider.loadAllDisplayNames()
        val types = arrayOf(NameType.LONG_STANDARD, NameType.SHORT_STANDARD, NameType.LONG_DAYLIGHT, NameType.SHORT_DAYLIGHT)
        val now = currentTimeMillis()
        val result = arrayOfNulls<Array<String?>>(ids.size)
        for (i in ids.indices) {
            val canonical = canonicalID(ids[i]) ?: ids[i]
            val row = arrayOfNulls<String>(5)
            result[i] = row
            row[0] = ids[i]
            provider.getDisplayNames(canonical, types, now, row, 1)
        }
        state.storedZoneStrings = result
        // ICU returns the stored matrix on the first call, copies on subsequent calls.
        return result
    }

    companion object {
        /** One instance belongs to the public factory; its cache keys omit locale keywords. */
        fun withCommonNames(currentTimeMillis: () -> Long, defaultCanonicalZoneID: () -> String?): DateFormatSymbolsZoneStringsCore {
            val cache = object : SoftCache<String, Names, ULocale?>() {
                override fun createInstance(key: String, data: ULocale?): Names {
                    val core = TimeZoneNamesDataCore(data, defaultCanonicalZoneID)
                    return object : Names {
                        override fun loadAllDisplayNames() = core.loadAllDisplayNames()
                        override fun getDisplayNames(id: String, types: Array<NameType>, date: Long, dest: Array<String?>, offset: Int) =
                            core.getDisplayNames(id, types, date, dest, offset)
                    }
                }
            }
            return DateFormatSymbolsZoneStringsCore(currentTimeMillis, { cache.getInstance(it!!.getBaseName(), it)!! })
        }
    }
}
