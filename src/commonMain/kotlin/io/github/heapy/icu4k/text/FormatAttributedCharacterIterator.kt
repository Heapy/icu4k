package io.github.heapy.icu4k.text

/** UTF-16 character iteration with independently constrained attribute runs. */
expect interface FormatAttributedCharacterIterator {
    fun first(): Char
    fun last(): Char
    fun current(): Char
    fun next(): Char
    fun previous(): Char
    fun setIndex(position: Int): Char
    fun getBeginIndex(): Int
    fun getEndIndex(): Int
    fun getIndex(): Int
    fun clone(): Any
    fun getRunStart(): Int
    fun getRunStart(attribute: FormatAttribute?): Int
    fun getRunStart(attributes: Set<FormatAttribute>?): Int
    fun getRunLimit(): Int
    fun getRunLimit(attribute: FormatAttribute?): Int
    fun getRunLimit(attributes: Set<FormatAttribute>?): Int
    fun getAttributes(): Map<FormatAttribute, Any>
    fun getAttribute(attribute: FormatAttribute?): Any?
    fun getAllAttributeKeys(): Set<FormatAttribute>
}
const val FORMAT_CHARACTER_DONE: Char = '\uffff'

internal data class FormatAttributeRange(val field: FormatAttribute, val value: Any, val start: Int, val limit: Int)
internal expect fun attributedCharacterIterator(text: String, ranges: List<FormatAttributeRange>): FormatAttributedCharacterIterator
