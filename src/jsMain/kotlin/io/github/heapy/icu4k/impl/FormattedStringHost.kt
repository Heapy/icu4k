package io.github.heapy.icu4k.impl

internal actual fun fsbCharAt(values: CharArray, index: Int): Char {
    if (index !in values.indices) throw IndexOutOfBoundsException()
    return values[index]
}
internal actual fun fsbFieldAt(values: Array<out Any?>, index: Int): Any? {
    if (index !in values.indices) throw IndexOutOfBoundsException()
    return values[index]
}

private fun pair(high: Char, low: Char): Int = ((high.code - 0xd800) shl 10) + low.code - 0xdc00 + 0x10000
internal actual fun fsbCodePointAt(values: CharArray, index: Int, limit: Int): Int {
    if (index >= limit || limit < 0 || limit > values.size) throw IndexOutOfBoundsException()
    val first = fsbCharAt(values, index)
    return if (first.isHighSurrogate() && index + 1 < limit && fsbCharAt(values, index + 1).isLowSurrogate()) pair(first, fsbCharAt(values, index + 1)) else first.code
}
internal actual fun fsbCodePointBefore(values: CharArray, index: Int, start: Int): Int {
    if (index <= start || start < 0 || start >= values.size) throw IndexOutOfBoundsException()
    val last = fsbCharAt(values, index - 1)
    return if (last.isLowSurrogate() && index - 1 > start && fsbCharAt(values, index - 2).isHighSurrogate()) pair(fsbCharAt(values, index - 2), last) else last.code
}
