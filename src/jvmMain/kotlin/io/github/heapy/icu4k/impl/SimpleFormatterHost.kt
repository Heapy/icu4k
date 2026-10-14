package io.github.heapy.icu4k.impl

internal actual fun formatterValueAt(values: Array<out CharSequence?>, index: Int): CharSequence? = values[index]
internal actual fun appendFormatterLiteral(output: Appendable, pattern: CharSequence, start: Int, end: Int) {
    try { output.append(pattern, start, end) }
    catch (failure: java.io.IOException) { throw io.github.heapy.icu4k.util.ICUUncheckedIOException(failure) }
}

internal actual fun formatterLiteralBuilder(capacity: Int): StringBuilder = StringBuilder(capacity)
