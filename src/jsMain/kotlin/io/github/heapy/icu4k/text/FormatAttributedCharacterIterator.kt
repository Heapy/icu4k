package io.github.heapy.icu4k.text

actual interface FormatAttributedCharacterIterator {
    actual fun first(): Char
    actual fun last(): Char
    actual fun current(): Char
    actual fun next(): Char
    actual fun previous(): Char
    actual fun setIndex(position: Int): Char
    actual fun getBeginIndex(): Int
    actual fun getEndIndex(): Int
    actual fun getIndex(): Int
    actual fun clone(): Any
    actual fun getRunStart(): Int
    actual fun getRunStart(attribute: FormatAttribute?): Int
    actual fun getRunStart(attributes: Set<FormatAttribute>?): Int
    actual fun getRunLimit(): Int
    actual fun getRunLimit(attribute: FormatAttribute?): Int
    actual fun getRunLimit(attributes: Set<FormatAttribute>?): Int
    actual fun getAttributes(): Map<FormatAttribute, Any>
    actual fun getAttribute(attribute: FormatAttribute?): Any?
    actual fun getAllAttributeKeys(): Set<FormatAttribute>
}
internal actual fun attributedCharacterIterator(text: String, ranges: List<FormatAttributeRange>): FormatAttributedCharacterIterator = PortableAttributedCharacterIterator(text, ranges)
