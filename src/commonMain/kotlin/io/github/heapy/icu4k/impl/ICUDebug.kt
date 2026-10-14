// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Original one-time debug parameter snapshot and substring-based flag selection. */
object ICUDebug {
    private val settings = DebugSettings(debugParameters())
    @kotlin.jvm.JvmStatic fun enabled(): Boolean = settings.enabled()
    @kotlin.jvm.JvmStatic fun enabled(name: String?): Boolean = settings.enabled(name)
    @kotlin.jvm.JvmStatic fun value(arg: String?): String = settings.value(arg)
}
internal class DebugSettings(private val parameters: String?, private val emit: (String) -> Unit = { println(it) }) {
    private val debug = parameters != null
    private val help = debug && (parameters!!.isEmpty() || "help" in parameters)
    init { if (debug) emit("\nICUDebug=$parameters") }
    fun enabled(): Boolean = debug
    fun enabled(name: String?): Boolean {
        if (!debug) return false
        val result = parameters!!.indexOf(name!!) != -1
        if (help) emit("\nICUDebug.enabled($name) = $result")
        return result
    }
    fun value(arg: String?): String {
        var result = "false"
        if (debug) {
            var index = parameters!!.indexOf(arg!!)
            if (index != -1) {
                index += arg.length
                result = if (index < parameters.length && parameters[index] == '=') {
                    ++index
                    val limit = parameters.indexOf(',', index)
                    parameters.substring(index, if (limit == -1) parameters.length else limit)
                } else "true"
            }
            if (help) emit("\nICUDebug.value($arg) = $result")
        }
        return result
    }
}
internal expect fun debugParameters(): String?
