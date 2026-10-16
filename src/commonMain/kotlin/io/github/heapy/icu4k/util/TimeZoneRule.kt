// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Time-zone offsets and the instants at which they take effect. Names do not affect equivalence. */
abstract class TimeZoneRule(private val name: String?, private val rawOffset: Int, private val dstSavings: Int) : ICUSerializable {
    open fun getName(): String? = name
    open fun getRawOffset(): Int = rawOffset
    open fun getDSTSavings(): Int = dstSavings
    open fun isEquivalentTo(other: TimeZoneRule?): Boolean = rawOffset == other!!.rawOffset && dstSavings == other.dstSavings
    abstract fun getFirstStart(prevRawOffset: Int, prevDSTSavings: Int): Date?
    abstract fun getFinalStart(prevRawOffset: Int, prevDSTSavings: Int): Date?
    abstract fun getNextStart(base: Long, prevRawOffset: Int, prevDSTSavings: Int, inclusive: Boolean): Date?
    abstract fun getPreviousStart(base: Long, prevRawOffset: Int, prevDSTSavings: Int, inclusive: Boolean): Date?
    abstract fun isTransitionRule(): Boolean
    override fun toString(): String = "name=$name, stdOffset=$rawOffset, dstSaving=$dstSavings"
    private companion object { private const val serialVersionUID = 6374143828553768100L }
}
