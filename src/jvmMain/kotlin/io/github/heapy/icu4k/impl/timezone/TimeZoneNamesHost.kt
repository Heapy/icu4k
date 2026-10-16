// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone
internal actual fun timeZoneNamesAssertionsEnabled(): Boolean = TimeZoneNamesDataCore::class.java.desiredAssertionStatus()
internal actual fun internTimeZoneNameID(value: String): String = value.intern()
internal actual fun immutableMetaZoneIDSet(values: Collection<String>): Set<String> =
    java.util.Collections.unmodifiableSet(java.util.HashSet<String>(values.size).apply { addAll(values) })
internal actual fun immutableAllMetaZoneIDSet(values: Set<String>): Set<String> =
    java.util.Collections.unmodifiableSet(java.util.TreeSet(values))
internal actual fun <V> createTimeZoneNamesMap(): MutableMap<String,V> = java.util.concurrent.ConcurrentHashMap()
internal actual fun <V> createTimeZoneNamesLoaderMap(): MutableMap<io.github.heapy.icu4k.impl.UResource.Key,V> = java.util.HashMap(300)
internal actual fun <T> createTimeZoneNameMatches(): MutableCollection<T> = java.util.LinkedList()
internal actual fun <T> emptyTimeZoneNameMatches(): MutableCollection<T> = java.util.Collections.emptyList()
