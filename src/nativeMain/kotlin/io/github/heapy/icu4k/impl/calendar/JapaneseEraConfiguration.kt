// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.heapy.icu4k.impl.calendar

import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun japaneseEraConfiguration(): JapaneseCalendarCore.Companion.Configuration = object : JapaneseCalendarCore.Companion.Configuration {
    override fun systemProperty(name: String): String? = null
    override fun environmentVariable(name: String): String? = getenv(name)?.toKString()
}
