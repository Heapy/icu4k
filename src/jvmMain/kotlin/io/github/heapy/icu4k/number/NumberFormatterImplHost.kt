package io.github.heapy.icu4k.number

internal actual fun assertNumberFormatterImpl(condition: () -> Boolean) {
    if (NumberFormatterImpl::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
