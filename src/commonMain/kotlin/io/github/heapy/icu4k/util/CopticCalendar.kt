// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.text.RbnfObjectStreamField

import io.github.heapy.icu4k.impl.calendar.CopticCalendarCore

class CopticCalendar : CECalendar {
    constructor() : super()
    @Suppress("DEPRECATION")
    constructor(locale: ULocale?) : this(TimeZone.forULocaleOrDefault(locale), locale)
    constructor(date: Date?) : super(date)
    constructor(year: Int, month: Int, date: Int) : super(year, month, date)
    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) : super(year, month, date, hour, minute, second)
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale)
    constructor(zone: TimeZone?) : this(zone, ULocale.getDefault(ULocale.Category.FORMAT))
    private constructor(other: CopticCalendar, copyMarker: Unit) : super(other, copyMarker)
    private fun coptic() = CopticCalendarCore(object : CopticCalendarCore.Owner {
        override fun newerField(defaultField: Int, alternateField: Int): Int = this@CopticCalendar.newerField(defaultField, alternateField)
        override fun internalGet(field: Int, defaultValue: Int): Int = this@CopticCalendar.internalGet(field, defaultValue)
    })
    override fun handleGetExtendedYear(): Int = coptic().handleGetExtendedYear()
    override fun extendedYearToEra(year: Int): Int = coptic().extendedYearToEra(year)
    override fun extendedYearToYear(year: Int): Int = coptic().extendedYearToYear(year)
    override fun getJDEpochOffset(): Int = coptic().getJDEpochOffset()
    override fun getRelatedYearDifference(): Int = coptic().getRelatedYearDifference()
    override fun getType(): String = coptic().getType()
    override fun isEra0CountingBackward(): Boolean = coptic().isEra0CountingBackward()
    override fun cloneNative(): Calendar = CopticCalendar(this, Unit)
    companion object {
        private const val serialVersionUID = 5903818751846742911L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = timeStateSerialFields("CopticCalendar")
        const val TOUT = 0; const val BABA = 1; const val HATOR = 2; const val KIAHK = 3
        const val TOBA = 4; const val AMSHIR = 5; const val BARAMHAT = 6; const val BARAMOUDA = 7
        const val BASHANS = 8; const val PAONA = 9; const val EPEP = 10; const val MESRA = 11; const val NASIE = 12
        fun copticToJD(year: Long, month: Int, day: Int): Int = CopticCalendarCore.copticToJD(year, month, day)
        fun ceToJD(year: Long, month: Int, day: Int, epoch: Int): Int = CECalendar.ceToJD(year, month, day, epoch)
        fun jdToCE(julianDay: Int, epoch: Int, fields: IntArray?) = CECalendar.jdToCE(julianDay, epoch, fields)
    }
}
