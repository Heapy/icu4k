package io.github.heapy.icu4k.impl.number

internal actual fun assertInvalidNumberPatternStyle() {
    if (io.github.heapy.icu4k.text.NumberFormat::class.java.desiredAssertionStatus()) throw AssertionError()
}
