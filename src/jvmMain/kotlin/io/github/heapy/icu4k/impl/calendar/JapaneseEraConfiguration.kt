// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

internal actual fun japaneseEraConfiguration(): JapaneseCalendarCore.Companion.Configuration = object : JapaneseCalendarCore.Companion.Configuration {
    override fun systemProperty(name: String): String? = System.getProperty(name)
    override fun environmentVariable(name: String): String? = System.getenv(name)
}
