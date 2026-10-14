package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

internal actual fun initialPlatformLocaleName(): String = ULocale(NSLocale.currentLocale.localeIdentifier).getName()
