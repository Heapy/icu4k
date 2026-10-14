package io.github.heapy.icu4k.impl.locale

internal actual fun <K, V> localeReadOnlyMap(source: Map<K, V>): Map<K, V> = java.util.Collections.unmodifiableMap(source)
