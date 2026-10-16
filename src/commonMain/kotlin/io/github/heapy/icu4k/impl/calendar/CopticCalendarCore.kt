// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

internal class CopticCalendarCore(private val owner: Owner) {
    interface Owner {
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
    }
    fun handleGetExtendedYear(): Int {
        if (owner.newerField(19, 1) == 19) return owner.internalGet(19, 1)
        return if (owner.internalGet(0, 1) == 0) 1 - owner.internalGet(1, 1) else owner.internalGet(1, 1)
    }
    fun extendedYearToEra(year: Int): Int = if (year <= 0) 0 else 1
    fun extendedYearToYear(year: Int): Int = if (year <= 0) 1 - year else year
    fun getJDEpochOffset(): Int = 1824665
    fun getRelatedYearDifference(): Int = 284
    fun getType(): String = "coptic"
    fun isEra0CountingBackward(): Boolean = true
    companion object {
        fun copticToJD(year: Long, month: Int, day: Int): Int = CECalendarCore.ceToJD(year, month, day, 1824665)
    }
}
