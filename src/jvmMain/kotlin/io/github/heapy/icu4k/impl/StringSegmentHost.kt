package io.github.heapy.icu4k.impl

internal actual fun assertStringSegment(condition: () -> Boolean) {
    if (StringSegment::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
