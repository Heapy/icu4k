package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun resourceDefaultLocaleName(): String {
    for (key in listOf("LC_ALL", "LC_CTYPE", "LANG")) {
        val value = getenv(key)?.toKString()
        if (!value.isNullOrEmpty()) return if (value == "C" || value == "POSIX" || value.startsWith("C.")) "en_US_POSIX"
            else ULocale(value).getName()
    }
    return "en_US_POSIX"
}
