package io.github.heapy.icu4k.impl.number

internal actual fun assertGrouper(condition: Boolean) {
    if (Grouper::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}
