// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.heapy.icu4k.impl

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun urlHandlerError(message: Any?) {
    val bytes = ((message?.toString() ?: "null") + "\n").encodeToByteArray()
    bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.convert(), stderr) }
}
