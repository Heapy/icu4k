package io.github.heapy.icu4k.impl.number

internal actual fun assertMutablePatternModifier(condition: () -> Boolean) {
    if (MutablePatternModifier::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
