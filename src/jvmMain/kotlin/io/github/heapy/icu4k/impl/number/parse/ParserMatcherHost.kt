package io.github.heapy.icu4k.impl.number.parse

internal actual fun assertParsedNumber(condition: () -> Boolean) {
    if (ParsedNumber::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
internal actual fun assertSeriesMatcher(condition: () -> Boolean) {
    if (SeriesMatcher::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
internal actual fun assertDecimalMatcher(condition: () -> Boolean) { if (DecimalMatcher::class.java.desiredAssertionStatus() && !condition()) throw AssertionError() }
