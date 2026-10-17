// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUConfig
import io.github.heapy.icu4k.impl.TimeZoneNamesFactoryImpl
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun TimeZoneNames.Companion.getInstance(locale: Locale?): TimeZoneNames? = getInstance(ULocale.forLocale(locale))

internal actual fun timeZoneNamesFactory(): TimeZoneNames.Factory {
    val defaultName = "com.ibm.icu.impl.TimeZoneNamesFactoryImpl"
    val name = ICUConfig.get("com.ibm.icu.text.TimeZoneNames.Factory.impl", defaultName)!!
    if (name == defaultName) return TimeZoneNamesFactoryImpl()
    try { return Class.forName(name).newInstance() as TimeZoneNames.Factory }
    catch (_: ClassNotFoundException) { }
    catch (_: IllegalAccessException) { }
    catch (_: InstantiationException) { }
    return TimeZoneNamesFactoryImpl()
}
