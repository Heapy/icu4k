// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore
import io.github.heapy.icu4k.impl.TZDBTimeZoneNames
import io.github.heapy.icu4k.util.ICUSerializable
import io.github.heapy.icu4k.util.ULocale

/** Localized time-zone and metazone names, including prefix matches for parsing. */
abstract class TimeZoneNames protected constructor() : ICUSerializable {
    enum class NameType {
        LONG_GENERIC, LONG_STANDARD, LONG_DAYLIGHT,
        SHORT_GENERIC, SHORT_STANDARD, SHORT_DAYLIGHT, EXEMPLAR_LOCATION
    }
    abstract fun getAvailableMetaZoneIDs(): Set<String>
    abstract fun getAvailableMetaZoneIDs(tzID: String?): Set<String>
    abstract fun getMetaZoneID(tzID: String?, date: Long): String?
    abstract fun getReferenceZoneID(mzID: String?, region: String?): String?
    abstract fun getMetaZoneDisplayName(mzID: String?, type: NameType?): String?
    abstract fun getTimeZoneDisplayName(tzID: String?, type: NameType?): String?

    fun getDisplayName(tzID: String?, type: NameType?, date: Long): String? =
        getTimeZoneDisplayName(tzID, type) ?: getMetaZoneDisplayName(getMetaZoneID(tzID, date), type)

    open fun getExemplarLocationName(tzID: String?): String? =
        TimeZoneNamesDataCore.getDefaultExemplarLocationName(tzID)

    open fun find(text: CharSequence?, start: Int, types: Set<NameType>?): Collection<MatchInfo> =
        throw UnsupportedOperationException("The method is not implemented in TimeZoneNames base class.")

    open class MatchInfo(nameType: NameType?, private val tzID: String?, private val mzID: String?, private val matchLength: Int) {
        private val nameType: NameType
        init {
            if (nameType == null) throw IllegalArgumentException("nameType is null")
            if (tzID == null && mzID == null) throw IllegalArgumentException("Either tzID or mzID must be available")
            if (matchLength <= 0) throw IllegalArgumentException("matchLength must be positive value")
            this.nameType = nameType
        }
        open fun tzID(): String? = tzID
        open fun mzID(): String? = mzID
        open fun nameType(): NameType = nameType
        open fun matchLength(): Int = matchLength
    }

    @Deprecated("This API is ICU internal only.")
    open fun loadAllDisplayNames() {}

    @Deprecated("This API is ICU internal only.")
    open fun getDisplayNames(tzID: String?, types: Array<out NameType?>?, date: Long, dest: Array<String?>?, destOffset: Int) {
        if (tzID.isNullOrEmpty()) return
        var mzID: String? = null
        for (i in types!!.indices) {
            val type = types[i]
            var name = getTimeZoneDisplayName(tzID, type)
            if (name == null) {
                if (mzID == null) mzID = getMetaZoneID(tzID, date)
                name = getMetaZoneDisplayName(mzID, type)
            }
            dest!![destOffset + i] = name
        }
    }

    @Deprecated("This API is ICU internal only.")
    abstract class Factory protected constructor() {
        @Deprecated("This API is ICU internal only.")
        abstract fun getTimeZoneNames(locale: ULocale?): TimeZoneNames?
    }

    companion object {
        private const val serialVersionUID = -9180227029248969153L
        @Suppress("DEPRECATION")
        private val factory: Factory = timeZoneNamesFactory()
        private val cache = object : SoftCache<String, TimeZoneNames, ULocale>() {
            @Suppress("DEPRECATION")
            override fun createInstance(key: String, data: ULocale): TimeZoneNames? = factory.getTimeZoneNames(data)
        }
        @kotlin.jvm.JvmStatic
        fun getInstance(locale: ULocale?): TimeZoneNames? = cache.getInstance(locale!!.getBaseName(), locale)
        @kotlin.jvm.JvmStatic
        fun getTZDBInstance(locale: ULocale?): TimeZoneNames = TZDBTimeZoneNames(locale)
    }
}

@Suppress("DEPRECATION")
internal expect fun timeZoneNamesFactory(): TimeZoneNames.Factory
internal fun TimeZoneNames.NameType.toCore(): TimeZoneNamesDataCore.NameType = TimeZoneNamesDataCore.NameType.valueOf(name)
internal fun TimeZoneNamesDataCore.MatchInfo.toPublic(): TimeZoneNames.MatchInfo =
    TimeZoneNames.MatchInfo(TimeZoneNames.NameType.valueOf(nameType().name), tzID(), mzID(), matchLength())
internal fun Collection<TimeZoneNamesDataCore.MatchInfo>.publicMatches(): Collection<TimeZoneNames.MatchInfo> {
    if (isEmpty()) return io.github.heapy.icu4k.impl.timezone.emptyTimeZoneNameMatches()
    return io.github.heapy.icu4k.impl.timezone.createTimeZoneNameMatches<TimeZoneNames.MatchInfo>().also { target ->
        for (match in this) target.add(match.toPublic())
    }
}
