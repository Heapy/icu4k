package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale

internal actual fun resourceDefaultLocaleName(): String =
    ULocale.forLanguageTag(js("Intl.DateTimeFormat().resolvedOptions().locale") as String).getName()
