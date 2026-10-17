// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Preserve Class.newInstance's constructor access and exception behavior. */
@Suppress("DEPRECATION")
internal fun <T : Any> TransliteratorRegistryCore<T>.putClass(id: String, type: Class<out T>, visible: Boolean) =
    putClass(id, {
        try { type.newInstance() }
        catch (_: InstantiationException) { null }
        catch (_: IllegalAccessException) { null }
    }, visible)
