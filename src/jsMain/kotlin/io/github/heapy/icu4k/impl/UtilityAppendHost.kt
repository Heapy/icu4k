package io.github.heapy.icu4k.impl

internal actual fun appendUtilityText(appendable: Appendable, string: CharSequence) {
    appendable.append(string)
}

internal actual fun appendUtilityNumberPart(appendable: Appendable, text: CharSequence, character: Boolean) {
    if(character)appendable.append(text[0])else appendable.append(text)
}
