// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** A transition instant and its before/after rules, retaining the supplied rule identities. */
open class TimeZoneTransition(private val time: Long, private val from: TimeZoneRule?, private val to: TimeZoneRule?) {
    open fun getTime(): Long = time
    open fun getFrom(): TimeZoneRule? = from
    open fun getTo(): TimeZoneRule? = to
    override fun toString(): String = "time=$time, from={$from}, to={$to}"
}
