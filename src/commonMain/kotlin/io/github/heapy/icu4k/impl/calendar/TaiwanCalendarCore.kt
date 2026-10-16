// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2010, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** TaiwanCalendar's Gregorian year offset and era policies. */
internal class TaiwanCalendarCore(private val owner: Owner) {
    interface Owner {
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun internalGet(field: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun internalSet(field: Int, value: Int)
        fun gregorianComputeFields(julianDay: Int)
        fun gregorianGetLimit(field: Int, limitType: Int): Int
    }
    fun handleGetExtendedYear(): Int {
        if (owner.newerField(19, 1) == 19 && owner.newerField(19, 0) == 19) return owner.internalGet(19, 1970)
        return if (owner.internalGet(0, 1) == 1) owner.internalGet(1, 1) + 1911
        else 1 - owner.internalGet(1, 1) + 1911
    }
    fun handleComputeFields(julianDay: Int) {
        owner.gregorianComputeFields(julianDay)
        val year = owner.internalGet(19) - 1911
        if (year > 0) { owner.internalSet(0, 1); owner.internalSet(1, year) }
        else { owner.internalSet(0, 0); owner.internalSet(1, 1 - year) }
    }
    fun handleGetLimit(field: Int, limitType: Int): Int = if (field == 0) {
        if (limitType == 0 || limitType == 1) 0 else 1
    } else owner.gregorianGetLimit(field, limitType)
    fun getType(): String = "roc"
}
