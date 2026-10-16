// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone
internal actual fun timeZoneNamesAssertionsEnabled(): Boolean = false
internal actual fun internTimeZoneNameID(value: String): String = value
@Suppress("UNCHECKED_CAST")
internal actual fun immutableMetaZoneIDSet(values: Collection<String>): Set<String> =
    ReadOnlyMetaZoneIDSet(HashSet<String>(values.size).apply { addAll(values) }) as Set<String>
@Suppress("UNCHECKED_CAST")
internal actual fun immutableAllMetaZoneIDSet(values: Set<String>): Set<String> =
    ReadOnlyMetaZoneIDSet(values, rejectNull = true) as Set<String>
internal actual fun <V> createTimeZoneNamesMap(): MutableMap<String,V> = HashMap()
internal actual fun <V> createTimeZoneNamesLoaderMap(): MutableMap<io.github.heapy.icu4k.impl.UResource.Key,V> = HashMap(300)
internal actual fun <T> createTimeZoneNameMatches(): MutableCollection<T> = ArrayList()
@Suppress("UNCHECKED_CAST")
internal actual fun <T> emptyTimeZoneNameMatches(): MutableCollection<T> = EmptyTimeZoneNameMatches as MutableCollection<T>
