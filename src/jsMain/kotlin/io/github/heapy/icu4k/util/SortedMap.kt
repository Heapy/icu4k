// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Common boundary for the sorted-map contract used by ICU services. */
actual interface SortedMap<K, V> : MutableMap<K, V> {
    actual fun comparator(): Comparator<in K>?
    actual fun firstKey(): K
    actual fun lastKey(): K
    actual fun headMap(toKey: K): SortedMap<K, V>
    actual fun tailMap(fromKey: K): SortedMap<K, V>
    actual fun subMap(fromKey: K, toKey: K): SortedMap<K, V>
}
