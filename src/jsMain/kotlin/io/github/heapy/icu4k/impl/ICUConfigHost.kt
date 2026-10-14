package io.github.heapy.icu4k.impl

internal actual fun loadICUConfigProperties(target: MutableMap<String, String>) { target.putAll(ICUConfigDefaults.properties) }
internal actual fun getICUConfigHostProperty(name: String): String? {
    if ('\u0000' in name) return null
    val environment: dynamic = js("typeof process !== 'undefined' && process.env ? process.env : null")
    val value: dynamic = if (environment == null) null else environment[name]
    return if (jsTypeOf(value) == "string") value as String else null
}
