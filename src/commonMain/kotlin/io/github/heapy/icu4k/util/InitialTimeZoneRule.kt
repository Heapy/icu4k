// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2008, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Initial offsets with no transition start time. */
open class InitialTimeZoneRule(name: String?, rawOffset: Int, dstSavings: Int) : TimeZoneRule(name,rawOffset,dstSavings) {
    override fun isEquivalentTo(other: TimeZoneRule?): Boolean = other is InitialTimeZoneRule && super.isEquivalentTo(other)
    override fun getFirstStart(prevRawOffset: Int, prevDSTSavings: Int): Date? = null
    override fun getFinalStart(prevRawOffset: Int, prevDSTSavings: Int): Date? = null
    override fun getNextStart(base: Long, prevRawOffset: Int, prevDSTSavings: Int, inclusive: Boolean): Date? = null
    override fun getPreviousStart(base: Long, prevRawOffset: Int, prevDSTSavings: Int, inclusive: Boolean): Date? = null
    override fun isTransitionRule(): Boolean = false
    private companion object { private const val serialVersionUID = 1876594993064051206L }
}
