@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.timezone.TimeZoneGenericNamesCore.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.MatchInfo
import io.github.heapy.icu4k.util.*

internal object GenericNamesOracleSupport {
    fun names(core: TimeZoneNamesDataCore): Names = object : Names {
        override fun getAvailableMetaZoneIDs(id: String) = core.getAvailableMetaZoneIDs(id)
        override fun getMetaZoneID(id: String,date: Long) = core.getMetaZoneID(id,date)
        override fun getReferenceZoneID(id: String?,region: String) = core.getReferenceZoneID(id,region)
        override fun getMetaZoneDisplayName(id: String,type: NameType) = core.getMetaZoneDisplayName(id,type)
        override fun getTimeZoneDisplayName(id: String,type: NameType) = core.getTimeZoneDisplayName(id,type)
        override fun getDisplayName(id: String,type: NameType,date: Long) = core.getDisplayName(id,type,date)
        override fun getExemplarLocationName(id: String) = core.getExemplarLocationName(id)
        override fun find(text: String,start: Int,types: Set<NameType>) = core.find(text,start,types)
    }
    fun names(source: com.ibm.icu.text.TimeZoneNames): Names = object : Names {
        private fun type(type: NameType) = com.ibm.icu.text.TimeZoneNames.NameType.valueOf(type.name)
        override fun getAvailableMetaZoneIDs(id: String): Set<String>? = source.getAvailableMetaZoneIDs(id)
        override fun getMetaZoneID(id: String,date: Long) = source.getMetaZoneID(id,date)
        override fun getReferenceZoneID(id: String?,region: String) = source.getReferenceZoneID(id,region)
        override fun getMetaZoneDisplayName(id: String,type: NameType) = source.getMetaZoneDisplayName(id,type(type))
        override fun getTimeZoneDisplayName(id: String,type: NameType) = source.getTimeZoneDisplayName(id,type(type))
        override fun getDisplayName(id: String,type: NameType,date: Long) = source.getDisplayName(id,type(type),date)
        override fun getExemplarLocationName(id: String) = source.getExemplarLocationName(id)
        override fun find(text: String,start: Int,types: Set<NameType>): Collection<MatchInfo>? {
            val filter = java.util.EnumSet.noneOf(com.ibm.icu.text.TimeZoneNames.NameType::class.java)
            types.forEach { filter.add(type(it)) }
            return source.find(text,start,filter)?.map { match -> object : MatchInfo(NameType.LONG_GENERIC,"dummy",null,1) {
                override fun tzID() = match.tzID()
                override fun mzID() = match.mzID()
                override fun nameType() = NameType.valueOf(match.nameType().name)
                override fun matchLength() = match.matchLength()
            } }
        }
    }
    private fun transition(source: com.ibm.icu.util.TimeZoneTransition?): TimeZoneTransition? = source?.let {
        fun rule(rule: com.ibm.icu.util.TimeZoneRule?): TimeZoneRule? = rule?.let { object : InitialTimeZoneRule("",0,0) { override fun getDSTSavings() = it.dstSavings } }
        object : TimeZoneTransition(0,null,null) {
            override fun getTime() = it.time
            override fun getFrom() = rule(it.from)
            override fun getTo() = rule(it.to)
        }
    }
    open class Owner(private val names: Names?) : OwnerQueries<com.ibm.icu.util.TimeZone> {
        override fun getDefaultNames(locale: ULocale?): Names? = names
        override fun getDefaultTimeZone() = com.ibm.icu.util.TimeZone.getDefault()
        override fun getCanonicalCLDRID(zone: com.ibm.icu.util.TimeZone?) = com.ibm.icu.impl.ZoneMeta.getCanonicalCLDRID(zone)
        override fun getOffset(zone: com.ibm.icu.util.TimeZone?,date: Long,local: Boolean,offsets: IntArray) = zone!!.getOffset(date,local,offsets)
        override fun getBasicTimeZone(zone: com.ibm.icu.util.TimeZone?): BasicTimeZoneCore? {
            if (zone !is com.ibm.icu.util.BasicTimeZone) return null
            return object : BasicTimeZoneCore() {
                override fun getID() = zone.id
                override fun getOffset(date: Long,local: Boolean,offsets: IntArray) = zone.getOffset(date,local,offsets)
                override fun getNextTransition(base: Long,inclusive: Boolean) = transition(zone.getNextTransition(base,inclusive))
                override fun getPreviousTransition(base: Long,inclusive: Boolean) = transition(zone.getPreviousTransition(base,inclusive))
                override fun getTimeZoneRules(): Array<TimeZoneRule?>? = error("Generic names do not query full rules")
            }
        }
        override fun getFrozenTimeZone(id: String) = com.ibm.icu.util.TimeZone.getFrozenTimeZone(id)
        override fun createPatternFormatter(pattern: String?): PatternFormatter {
            val formatter = java.text.MessageFormat(pattern)
            return PatternFormatter { formatter.format(it) }
        }
    }
}
