package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale

internal actual fun initialPlatformLocaleName(): String =
    ULocale.forLanguageTag(java.util.Locale.getDefault().toLanguageTag()).getName()
