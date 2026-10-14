package io.github.heapy.icu4k.impl

internal actual fun checkedArrayElement(values: Array<String>, index: Int): String {
    if (index !in values.indices) throw IndexOutOfBoundsException("Index $index out of bounds for length ${values.size}")
    return values[index]
}
internal actual fun checkedArrayElement(values: IntArray, index: Int): Int {
    if (index !in values.indices) throw IndexOutOfBoundsException("Index $index out of bounds for length ${values.size}")
    return values[index]
}

internal actual fun checkedSetArrayElement(values: BooleanArray, index: Int, value: Boolean) {
    if (index !in values.indices) throw IndexOutOfBoundsException("Index $index out of bounds for length ${values.size}")
    values[index] = value
}

internal actual fun checkedSetArrayElement(values: Array<String?>, index: Int, value: String?) {
    if (index !in values.indices) throw IndexOutOfBoundsException("Index $index out of bounds for length ${values.size}")
    values[index] = value
}
