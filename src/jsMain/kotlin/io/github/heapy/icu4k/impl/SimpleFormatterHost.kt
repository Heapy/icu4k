package io.github.heapy.icu4k.impl

internal actual fun formatterValueAt(values: Array<out CharSequence?>, index: Int): CharSequence? {
    if (index !in values.indices) throw IndexOutOfBoundsException("Index $index out of bounds for length ${values.size}")
    return values[index]
}
internal actual fun appendFormatterLiteral(output: Appendable, pattern: CharSequence, start: Int, end: Int) { output.append(pattern, start, end) }

internal actual fun formatterLiteralBuilder(capacity: Int): StringBuilder {
    if (capacity < 0) throw IllegalArgumentException("Negative capacity: $capacity")
    return StringBuilder(capacity)
}
