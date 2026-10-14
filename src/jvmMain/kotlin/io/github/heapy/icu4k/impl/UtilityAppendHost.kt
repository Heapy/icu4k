package io.github.heapy.icu4k.impl

internal actual fun appendUtilityText(appendable: Appendable, string: CharSequence) {
    try { appendable.append(string) }
    catch (failure: java.io.IOException) { throw io.github.heapy.icu4k.util.ICUUncheckedIOException(failure) }
}
