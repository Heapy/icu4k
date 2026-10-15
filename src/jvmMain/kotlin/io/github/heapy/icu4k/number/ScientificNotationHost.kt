package io.github.heapy.icu4k.number

internal actual fun assertScientificNotation(condition: () -> Boolean) {
    if (ScientificNotation::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
