package io.github.heapy.icu4k.impl.locale

internal actual fun <E> localeReadOnlyList(source: List<E>): List<E> = java.util.Collections.unmodifiableList(source)
