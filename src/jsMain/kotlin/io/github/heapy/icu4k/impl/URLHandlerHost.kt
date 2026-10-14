// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl
internal actual fun urlHandlerError(message: Any?) { js("console.error")(message?.toString() ?: "null") }
