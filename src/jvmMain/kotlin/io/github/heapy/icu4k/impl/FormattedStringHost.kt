package io.github.heapy.icu4k.impl

internal actual fun fsbCharAt(values: CharArray, index: Int): Char = values[index]
internal actual fun fsbFieldAt(values: Array<out Any?>, index: Int): Any? = values[index]

internal actual fun fsbCodePointAt(values: CharArray, index: Int, limit: Int): Int = Character.codePointAt(values, index, limit)
internal actual fun fsbCodePointBefore(values: CharArray, index: Int, start: Int): Int = Character.codePointBefore(values, index, start)

internal actual fun assertFormattedStringBuilder(condition: () -> Boolean) {
    if (FormattedStringBuilder::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
