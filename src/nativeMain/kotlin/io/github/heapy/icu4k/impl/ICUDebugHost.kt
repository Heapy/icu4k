package io.github.heapy.icu4k.impl

internal actual fun debugParameters(): String? = getICUConfigHostProperty("ICUDebug")
