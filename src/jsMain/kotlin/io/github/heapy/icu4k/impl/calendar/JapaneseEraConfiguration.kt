// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

internal actual fun japaneseEraConfiguration(): JapaneseCalendarCore.Companion.Configuration = object : JapaneseCalendarCore.Companion.Configuration {
    override fun systemProperty(name: String): String? = null
    override fun environmentVariable(name: String): String? {
        val environment: dynamic = js("typeof process !== 'undefined' && process.env ? process.env : null")
        val value: dynamic = if (environment == null) null else environment[name]
        return if (jsTypeOf(value) == "string") value as String else null
    }
}
