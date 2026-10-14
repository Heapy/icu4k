package io.github.heapy.icu4k.impl

internal actual fun checkedArrayElement(values: Array<String>, index: Int): String = values[index]
internal actual fun checkedArrayElement(values: IntArray, index: Int): Int = values[index]

internal actual fun checkedSetArrayElement(values: BooleanArray, index: Int, value: Boolean) {
    values[index] = value
}
