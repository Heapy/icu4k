package io.github.heapy.icu4k.impl

internal actual fun appendUtilityText(appendable: Appendable, string: CharSequence) {
    try { appendable.append(string) }
    catch (failure: java.io.IOException) { throw io.github.heapy.icu4k.util.ICUUncheckedIOException(failure) }
}

internal actual fun appendUtilityNumberPart(appendable: Appendable, text: CharSequence, character: Boolean) {
    try { if(character)appendable.append(text[0])else appendable.append(text) }
    catch (failure: java.io.IOException) { throw IllegalIcuArgumentException(failure) }
}
