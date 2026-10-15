package io.github.heapy.icu4k.impl.number

internal actual fun assertDecimalBcd(condition: Boolean) {
    if (DecimalBcdStorage::class.java.desiredAssertionStatus() && !condition) throw AssertionError()
}

internal actual fun assertDecimalBcd(condition: () -> Boolean) {
    if (DecimalBcdStorage::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
