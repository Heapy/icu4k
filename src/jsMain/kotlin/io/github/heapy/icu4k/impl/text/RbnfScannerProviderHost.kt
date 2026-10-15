// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.text

import io.github.heapy.icu4k.util.ULocale

internal actual fun rbnfScannerLocale(locale: ULocale?): ULocale? = portableRbnfScannerLocale(locale)
