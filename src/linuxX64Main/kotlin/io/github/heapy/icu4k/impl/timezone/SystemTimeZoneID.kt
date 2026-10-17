// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.heapy.icu4k.impl.timezone

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun nativeSystemTimeZoneID(): String {
    getenv("TZ")?.toKString()?.let { value ->
        // POSIX explicitly defines an empty TZ as UTC; this is not a missing-ID fallback.
        if (value.isEmpty()) return "Etc/UTC"
        val id = value.removePrefix(":")
        return id.substringAfter("/zoneinfo/", id)
    }
    val resolved = realpath("/etc/localtime", null)
    if (resolved != null) {
        val path = try { resolved.toKString() } finally { free(resolved) }
        if ("/zoneinfo/" in path) return path.substringAfter("/zoneinfo/")
    }
    val file = fopen("/etc/timezone", "r")
    if (file != null) try {
        val id = memScoped { val buffer = allocArray<ByteVar>(1024); fgets(buffer, 1024, file)?.toKString()?.trim() }
        if (!id.isNullOrEmpty()) return id
    } finally { fclose(file) }
    error("The Linux host did not expose its system timezone ID through TZ, /etc/localtime or /etc/timezone.")
}
