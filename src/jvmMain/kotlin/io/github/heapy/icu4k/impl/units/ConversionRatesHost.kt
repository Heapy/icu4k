package io.github.heapy.icu4k.impl.units
internal actual fun assertConversionRates(condition: () -> Boolean) {
    if (ConversionRates::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
internal actual fun assertConversionRates(condition: Boolean, message: () -> String) {
    if (ConversionRates::class.java.desiredAssertionStatus() && !condition) throw AssertionError(message())
}
