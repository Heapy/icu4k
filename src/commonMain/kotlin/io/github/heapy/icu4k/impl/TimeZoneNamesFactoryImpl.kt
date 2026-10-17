// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.TimeZoneNames
import io.github.heapy.icu4k.util.ULocale

open class TimeZoneNamesFactoryImpl : TimeZoneNames.Factory() {
    override fun getTimeZoneNames(locale: ULocale?): TimeZoneNames = TimeZoneNamesImpl(locale)
}
