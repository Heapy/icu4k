package io.github.heapy.icu4k.impl.locale

/** Host locale discovery only; locale-sensitive algorithms remain in Common Kotlin. */
internal expect fun defaultLocaleLanguage(): String
