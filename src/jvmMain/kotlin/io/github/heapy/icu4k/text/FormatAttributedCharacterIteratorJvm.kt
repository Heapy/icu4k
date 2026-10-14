package io.github.heapy.icu4k.text

actual typealias FormatAttributedCharacterIterator = java.text.AttributedCharacterIterator
internal actual fun attributedCharacterIterator(text: String, ranges: List<FormatAttributeRange>): FormatAttributedCharacterIterator {
    val result = java.text.AttributedString(text)
    for (range in ranges) result.addAttribute(range.field, range.value, range.start, range.limit)
    return result.iterator
}
