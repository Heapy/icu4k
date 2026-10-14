// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Common boundary for the sorted-map contract used by ICU services. */
expect interface SortedMap<K, V> : MutableMap<K, V> {
    fun comparator(): Comparator<in K>?
    fun firstKey(): K
    fun lastKey(): K
    fun headMap(toKey: K): SortedMap<K, V>
    fun tailMap(fromKey: K): SortedMap<K, V>
    fun subMap(fromKey: K, toKey: K): SortedMap<K, V>
}
