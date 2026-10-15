// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import io.github.heapy.icu4k.util.toLocale

internal actual fun rbnfScannerLocale(locale: ULocale?): ULocale? = ULocale.forLocale(locale!!.toLocale())
