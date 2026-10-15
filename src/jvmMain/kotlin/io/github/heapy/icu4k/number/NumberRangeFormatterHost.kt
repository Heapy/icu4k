package io.github.heapy.icu4k.number

internal actual fun assertNumberRangeFormatter(condition: () -> Boolean) {
    if (NumberRangeFormatterImpl::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
