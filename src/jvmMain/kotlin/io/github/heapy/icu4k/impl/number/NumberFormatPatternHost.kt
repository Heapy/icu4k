package io.github.heapy.icu4k.impl.number

internal actual fun assertInvalidNumberPatternStyle() {
    if (NumberFormatPatternData::class.java.desiredAssertionStatus()) throw AssertionError()
}
