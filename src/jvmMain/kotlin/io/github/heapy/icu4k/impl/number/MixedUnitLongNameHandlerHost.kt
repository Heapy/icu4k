package io.github.heapy.icu4k.impl.number

internal actual fun assertMixedUnitLongNameHandler(condition: () -> Boolean, message: () -> String?) {
    if (MixedUnitLongNameHandler::class.java.desiredAssertionStatus() && !condition()) {
        val text = message()
        if (text == null) throw AssertionError() else throw AssertionError(text)
    }
}
