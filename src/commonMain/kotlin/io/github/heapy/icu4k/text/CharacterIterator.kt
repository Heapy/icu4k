package io.github.heapy.icu4k.text

/** Common UTF-16 cursor contract replacing java.text.CharacterIterator. */
interface CharacterIterator {
    fun first(): Char
    fun last(): Char
    fun current(): Char
    fun next(): Char
    fun previous(): Char
    fun setIndex(position: Int): Char
    fun getBeginIndex(): Int
    fun getEndIndex(): Int
    fun getIndex(): Int
    fun clone(): CharacterIterator
    companion object { const val DONE: Char = '\uffff' }
}
