package io.github.heapy.icu4k.impl

internal actual fun loadICUConfigProperties(target: MutableMap<String, String>) { target.putAll(ICUConfigDefaults.properties) }
