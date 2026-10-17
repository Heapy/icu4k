// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2014, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.RuleBasedTimeZone
import io.github.heapy.icu4k.util.TimeZone
import io.github.heapy.icu4k.util.DateTimeRule
import io.github.heapy.icu4k.util.InitialTimeZoneRule
import io.github.heapy.icu4k.util.TimeArrayTimeZoneRule

/** DangiCalendar's source astronomical time zone, including its 1897 adjustment. */
internal object DangiCalendarCore {
    private val koreaZone = RuleBasedTimeZone("KOREA_ZONE", InitialTimeZoneRule("GMT+8", 8 * 3600000, 0)).apply {
        addTransitionRule(TimeArrayTimeZoneRule("Korean 1897",7 * 3600000,0,longArrayOf((1897 - 1970) * 365L * 86400000L),DateTimeRule.STANDARD_TIME))
        addTransitionRule(TimeArrayTimeZoneRule("Korean 1898-1911",8 * 3600000,0,longArrayOf((1898 - 1970) * 365L * 86400000L),DateTimeRule.STANDARD_TIME))
        addTransitionRule(TimeArrayTimeZoneRule("Korean 1912-",9 * 3600000,0,longArrayOf((1912 - 1970) * 365L * 86400000L),DateTimeRule.STANDARD_TIME))
        freeze()
    }
    fun astronomicalZone(): TimeZone = koreaZone
    fun astronomicalOffset(millis: Long): Int {
        val offsets = IntArray(2)
        koreaZone.getOffset(millis, false, offsets)
        return offsets[0] + offsets[1]
    }
    fun create(owner: ChineseCalendarCore.Owner): ChineseCalendarCore = ChineseCalendarCore(owner, ::astronomicalOffset)
    fun getType(): String = "dangi"
}
