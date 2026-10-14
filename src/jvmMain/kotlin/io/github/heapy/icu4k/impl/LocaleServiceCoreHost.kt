package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale

internal actual fun checkedSetLocaleArrayElement(values: Array<ULocale?>, index: Int, value: ULocale) {
    values[index] = value
}
