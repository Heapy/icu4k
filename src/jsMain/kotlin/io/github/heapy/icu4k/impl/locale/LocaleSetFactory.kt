package io.github.heapy.icu4k.impl.locale

internal actual fun <E : Comparable<E>> localeReadOnlySet(source: Set<E>): Set<E> = LocaleReadOnlySet({ source }, null)
