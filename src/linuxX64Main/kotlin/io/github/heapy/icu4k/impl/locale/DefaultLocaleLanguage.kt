package io.github.heapy.icu4k.impl.locale

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun defaultLocaleLanguage(): String {
    for (key in listOf("LC_ALL", "LC_CTYPE", "LANG")) {
        val value = getenv(key)?.toKString()
        if (!value.isNullOrEmpty()) return value.substringBefore('_').substringBefore('-').substringBefore('.')
    }
    return ""
}
