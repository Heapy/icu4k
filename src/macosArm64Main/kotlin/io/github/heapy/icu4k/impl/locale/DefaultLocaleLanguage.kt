package io.github.heapy.icu4k.impl.locale

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

internal actual fun defaultLocaleLanguage(): String = NSLocale.currentLocale.languageCode ?: ""
