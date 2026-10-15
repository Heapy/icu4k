package io.github.heapy.icu4k.impl.number

internal actual fun assertLongNameHandler(condition: () -> Boolean, message: () -> String?) {
    if (LongNameHandler::class.java.desiredAssertionStatus() && !condition()) {
        val text = message()
        if (text == null) throw AssertionError() else throw AssertionError(text)
    }
}
