package io.github.heapy.icu4k.impl.locale

internal actual fun defaultLocaleLanguage(): String =
    (js("Intl.DateTimeFormat().resolvedOptions().locale") as String).substringBefore('-')
