package io.github.heapy.icu4k.impl

internal actual fun debugParameters(): String? = try { System.getProperty("ICUDebug") } catch (_: SecurityException) { null }
