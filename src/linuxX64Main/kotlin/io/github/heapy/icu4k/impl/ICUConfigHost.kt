@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.heapy.icu4k.impl

import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun getICUConfigHostProperty(name: String): String? =
    if ('\u0000' in name) null else getenv(name)?.toKString()
