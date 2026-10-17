// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
internal actual fun dateTimePatternGeneratorAssertionsEnabled(): Boolean = false

internal actual fun dateTimePatternFirstChar(value: String): Char {
    if (value.isEmpty()) throw IndexOutOfBoundsException()
    return value[0]
}
