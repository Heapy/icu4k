package io.github.heapy.icu4k.impl.number

internal actual fun assertCompactData(condition: () -> Boolean) {
    if (CompactData::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
@Suppress("UNCHECKED_CAST")
internal actual fun compactPatternList(patterns: Array<String?>): MutableList<String?> = patterns.asList() as MutableList<String?>
