package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.SortedMap

internal actual fun createServiceDisplayNamesMap(comparator: Comparator<Any?>?): SortedMap<String?, String> = java.util.TreeMap(comparator)
internal actual fun readOnlyServiceDisplayNamesMap(map: SortedMap<String?, String>): SortedMap<String?, String> = java.util.Collections.unmodifiableSortedMap(map)
