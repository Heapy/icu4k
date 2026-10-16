// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2012, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** BuddhistCalendar's overrides of the Gregorian framework. */
internal class BuddhistCalendarCore(private val owner: Owner) {
    interface Owner {
        fun newerField(defaultField: Int, alternateField: Int): Int
        fun internalGet(field: Int): Int
        fun internalGet(field: Int, defaultValue: Int): Int
        fun internalSet(field: Int, value: Int)
        fun gregorianComputeFields(julianDay: Int)
        fun gregorianComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int
        fun gregorianGetLimit(field: Int, limitType: Int): Int
    }
    fun handleGetExtendedYear(): Int = if (owner.newerField(19, 1) == 19) owner.internalGet(19, 1970)
        else owner.internalGet(1, 2513) - 543
    fun handleComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int =
        owner.gregorianComputeMonthStart(year, month, useMonth)
    fun handleComputeFields(julianDay: Int) {
        owner.gregorianComputeFields(julianDay)
        val year = owner.internalGet(19) + 543
        owner.internalSet(0, 0)
        owner.internalSet(1, year)
    }
    fun handleGetLimit(field: Int, limitType: Int): Int = if (field == 0) 0 else owner.gregorianGetLimit(field, limitType)
    fun getType(): String = "buddhist"
    fun isEra0CountingBackward(): Boolean = false
}
