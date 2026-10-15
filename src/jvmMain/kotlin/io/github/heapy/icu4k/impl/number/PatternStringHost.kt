package io.github.heapy.icu4k.impl.number

internal actual fun assertPadder(condition: Boolean) {
    if (Padder::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}
internal actual fun assertPatternStringParser(condition: Boolean) {
    if (PatternStringParserCore::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}
