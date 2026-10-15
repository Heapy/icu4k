package io.github.heapy.icu4k.impl.number

internal actual fun assertPadder(condition: Boolean) {
    if (Padder::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}
internal actual fun assertPatternStringParser(condition: Boolean) {
    if (PatternStringParser::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}
internal actual fun assertPatternStringUtils(condition: Boolean) {
    if (PatternStringUtilsCore::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}
