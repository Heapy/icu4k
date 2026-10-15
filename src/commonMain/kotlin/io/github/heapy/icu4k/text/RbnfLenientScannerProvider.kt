// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale

/** Supplies locale-specific lenient scanners and optional extra collation rules. */
@Deprecated("ICU 54")
interface RbnfLenientScannerProvider {
    @Deprecated("ICU 54")
    fun get(locale: ULocale?, extras: String?): RbnfLenientScanner?
}
