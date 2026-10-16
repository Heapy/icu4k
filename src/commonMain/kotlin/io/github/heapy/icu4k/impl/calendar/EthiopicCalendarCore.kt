// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CalendarUtil
import io.github.heapy.icu4k.util.ULocale

internal class EthiopicCalendarCore(private val owner: Owner) {
    interface Owner {
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun isAmeteAlemEra(): Boolean
        fun setAmeteAlemEra(value: Boolean)
        fun baseGetLimit(field: Int, type: Int): Int
    }
    private var ameteAlem = false
    fun setAmeteAlemEra(value: Boolean) { ameteAlem = value }
    fun isAmeteAlemEra(): Boolean = ameteAlem
    fun copyFor(owner: Owner): EthiopicCalendarCore = EthiopicCalendarCore(owner).also { it.ameteAlem = ameteAlem }
    fun getType(): String = if (owner.isAmeteAlemEra()) "ethiopic-amete-alem" else "ethiopic"
    fun handleGetExtendedYear(): Int {
        if (owner.newerField(19, 1) == 19) return owner.internalGet(19, 1)
        if (owner.isAmeteAlemEra()) return owner.internalGet(1, 1)
        return if (owner.internalGet(0, 1) == 1) owner.internalGet(1, 1) else owner.internalGet(1, 1) - 5500
    }
    fun extendedYearToEra(year: Int): Int = if (owner.isAmeteAlemEra()) 0 else if (year <= 0) 0 else 1
    fun extendedYearToYear(year: Int): Int = if (owner.isAmeteAlemEra()) year else if (year <= 0) year + 5500 else year
    fun handleGetLimit(field: Int, type: Int): Int = if (owner.isAmeteAlemEra() && field == 0) 0 else owner.baseGetLimit(field, type)
    fun getJDEpochOffset(): Int = if (owner.isAmeteAlemEra()) -285019 else 1723856
    fun getRelatedYearDifference(): Int = 8
    fun setCalcTypeForLocale(locale: ULocale?) { owner.setAmeteAlemEra(CalendarUtil.getCalendarType(locale) == "ethiopic-amete-alem") }
    companion object {
        fun EthiopicToJD(year: Long, month: Int, day: Int): Int = CECalendarCore.ceToJD(year, month, day, 1723856)
    }
}
