package io.github.heapy.icu4k.impl.locale

// JS strings are primitive values, with identity comparison based on their contents.
internal actual fun internLocaleString(value: String): String = value
