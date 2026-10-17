// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl
internal actual fun <V> intervalPatternMap(initialCapacity: Int?): MutableMap<String?, V> = IntervalPatternMap(initialCapacity)
