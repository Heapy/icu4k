package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale

internal fun resourceDefaultLocaleName(): String = ULocale.getDefault().getName()
internal expect fun initialPlatformLocaleName(): String
