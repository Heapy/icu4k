package io.github.heapy.icu4k.impl

internal actual fun currencySpacingStringAt(values: Array<out String?>, index: Int): String? {
    return values[index]
}

internal actual fun currencySpacingBoundsFailure(index: Int, length: Int): Nothing = throw IndexOutOfBoundsException("Index $index out of bounds for length $length")
