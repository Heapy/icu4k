// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2009, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Immutable interval endpoints in epoch milliseconds; reversed endpoints are retained. */
class DateInterval(from: Long, to: Long) : ICUSerializable {
    private val fromDate = from
    private val toDate = to
    fun getFromDate(): Long = fromDate
    fun getToDate(): Long = toDate
    override fun equals(other: Any?): Boolean = other is DateInterval && fromDate == other.fromDate && toDate == other.toDate
    override fun hashCode(): Int = (fromDate + toDate).toInt()
    override fun toString(): String = "$fromDate $toDate"
    companion object { private const val serialVersionUID = 1L }
}
