package io.github.heapy.icu4k.number

internal actual fun assertCompactNotation(condition: () -> Boolean) {
    if (CompactNotation::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
internal actual fun compactPatternSet(): MutableSet<String> = java.util.HashSet()
