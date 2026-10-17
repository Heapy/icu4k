// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
internal actual fun dateIntervalFormatRead(input: PluralObjectInputStream): Unit = throw UnsupportedOperationException("Java serialization is only available on JVM")
internal actual fun dateIntervalCharAt(text: String, index: Int): Char {
    if (index < 0 || index >= text.length) throw IndexOutOfBoundsException("Index: $index, Length: ${text.length}")
    return text[index]
}
internal actual fun dateIntervalSubSequence(text: String, start: Int, end: Int): CharSequence {
    if (start < 0 || end > text.length || start > end) throw IndexOutOfBoundsException("Range: $start..$end, Length: ${text.length}")
    return text.subSequence(start, end)
}
