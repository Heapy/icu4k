package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale

internal actual fun checkedSetLocaleArrayElement(values: Array<ULocale?>, index: Int, value: ULocale) {
    if (index !in values.indices) throw IndexOutOfBoundsException("Index $index out of bounds for length ${values.size}")
    values[index] = value
}
