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
