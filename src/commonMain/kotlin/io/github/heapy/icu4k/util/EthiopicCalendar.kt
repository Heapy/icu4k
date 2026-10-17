// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.EthiopicCalendarCore

class EthiopicCalendar : CECalendar {
    private var eraState: EthiopicCalendarCore? = null
    private fun ethiopic(): EthiopicCalendarCore = eraState ?: EthiopicCalendarCore(Binding()).also { eraState = it }
    constructor() : this(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT))
    @Suppress("DEPRECATION")
    constructor(locale: ULocale?) : this(TimeZone.forULocaleOrDefault(locale), locale)
    constructor(date: Date?) : super(date)
    constructor(year: Int, month: Int, date: Int) : super(year, month, date)
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(year, month, date, hour, minute, second)
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale) {
        ethiopic().setCalcTypeForLocale(locale)
    }
    constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    private constructor(other: EthiopicCalendar, copyMarker: Unit) : super(other, copyMarker) {
        eraState = other.ethiopic().copyFor(Binding())
    }
    fun isAmeteAlemEra(): Boolean = ethiopic().isAmeteAlemEra()
    fun setAmeteAlemEra(value: Boolean) = ethiopic().setAmeteAlemEra(value)
    override fun handleGetExtendedYear(): Int = ethiopic().handleGetExtendedYear()
    override fun extendedYearToEra(year: Int): Int = ethiopic().extendedYearToEra(year)
    override fun extendedYearToYear(year: Int): Int = ethiopic().extendedYearToYear(year)
    override fun getJDEpochOffset(): Int = ethiopic().getJDEpochOffset()
    override fun getRelatedYearDifference(): Int = ethiopic().getRelatedYearDifference()
    override fun getType(): String = ethiopic().getType()
    override fun handleGetLimit(field: Int, limitType: Int): Int = ethiopic().handleGetLimit(field, limitType)
    override fun cloneNative(): Calendar = EthiopicCalendar(this, Unit)
    override fun clone(): Calendar = super.clone().also { copy ->
        (copy as EthiopicCalendar).eraState = ethiopic().copyFor(copy.Binding())
    }
    private inner class Binding : EthiopicCalendarCore.Owner {
        override fun newerField(defaultField: Int, alternateField: Int): Int = this@EthiopicCalendar.newerField(defaultField, alternateField)
        override fun internalGet(field: Int, defaultValue: Int): Int = this@EthiopicCalendar.internalGet(field, defaultValue)
        override fun isAmeteAlemEra(): Boolean = this@EthiopicCalendar.isAmeteAlemEra()
        override fun setAmeteAlemEra(value: Boolean) = this@EthiopicCalendar.setAmeteAlemEra(value)
        override fun baseGetLimit(field: Int, type: Int): Int = super@EthiopicCalendar.handleGetLimit(field, type)
    }
    companion object {
        const val MESKEREM = 0; const val TEKEMT = 1; const val HEDAR = 2; const val TAHSAS = 3
        const val TER = 4; const val YEKATIT = 5; const val MEGABIT = 6; const val MIAZIA = 7
        const val GENBOT = 8; const val SENE = 9; const val HAMLE = 10; const val NEHASSE = 11; const val PAGUMEN = 12
        fun EthiopicToJD(year: Long, month: Int, day: Int): Int = EthiopicCalendarCore.EthiopicToJD(year, month, day)
        fun ceToJD(year: Long, month: Int, day: Int, epoch: Int): Int = CECalendar.ceToJD(year, month, day, epoch)
        fun jdToCE(julianDay: Int, epoch: Int, fields: IntArray?) = CECalendar.jdToCE(julianDay, epoch, fields)
    }
}
