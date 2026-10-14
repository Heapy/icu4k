package io.github.heapy.icu4k.impl

internal actual fun supplementalRegionCase(value: String, upper: Boolean): String =
    if (upper) value.uppercase(java.util.Locale.getDefault()) else value.lowercase(java.util.Locale.getDefault())
