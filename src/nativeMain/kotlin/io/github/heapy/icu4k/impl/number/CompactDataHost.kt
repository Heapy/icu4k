package io.github.heapy.icu4k.impl.number

internal actual fun assertCompactData(condition: () -> Boolean) = Unit
internal actual fun compactPatternList(patterns: Array<String?>): MutableList<String?> = CompactPatternList(patterns)
