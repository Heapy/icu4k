package io.github.heapy.icu4k.impl.units
internal actual fun assertUnitsRouter(condition: () -> Boolean) {
    if (UnitsRouter::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
