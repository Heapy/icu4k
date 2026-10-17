// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.calendar.DangiCalendarCore

@Deprecated("This API is ICU internal only.")
open class DangiCalendar : ChineseCalendar {
    constructor() : this(TimeZone.getDefault(), ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(date: Date?) : this() { setTime(date) }
    constructor(zone: TimeZone?, locale: ULocale?) : super(zone, locale, DangiCalendarCore::astronomicalOffset)
    protected constructor(other: DangiCalendar, copyMarker: Unit) : super(other, copyMarker)
    override fun getType(): String = "dangi"
    override fun cloneNative(): Calendar = DangiCalendar(this, Unit)
}
