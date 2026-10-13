// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.text.SymbolTable
import io.github.heapy.icu4k.text.UTF16

/** Original rule iterator, including variable expansion and escaped code points. */
internal class RuleCharacterIterator(private val text: String, private val sym: SymbolTable?, private val pos: ParsePosition) {
    private var buf: String? = null
    private var bufPos = 0
    private var escaped = false
    init { require(pos.index <= text.length) }
    class Position internal constructor() {
        internal var buf: String? = null
        internal var bufPos = 0
        internal var posIndex = 0
    }
    fun atEnd(): Boolean = buf == null && pos.index == text.length
    fun next(options: Int): Int {
        escaped = false
        while (true) {
            var c = current()
            advance(UTF16.getCharCount(c))
            if (c == SymbolTable.SYMBOL_REF.code && buf == null && options and PARSE_VARIABLES != 0 && sym != null) {
                val name = sym.parseReference(text, pos, text.length) ?: return c
                bufPos = 0
                val chars = sym.lookup(name)
                if (chars == null) { buf = null; throw IllegalArgumentException("Undefined variable: $name") }
                if (chars.isEmpty()) buf = null
                buf = chars.concatToString()
                continue
            }
            if (options and SKIP_WHITESPACE != 0 && PatternProps.isWhiteSpace(c)) continue
            if (c == '\\'.code && options and PARSE_ESCAPES != 0) {
                val decoded = Utility.unescapeAndLengthAt(getCurrentBuffer(), getCurrentBufferPos())
                require(decoded >= 0) { "Invalid escape" }
                c = Utility.cpFromCodePointAndLength(decoded)
                jumpahead(Utility.lengthFromCodePointAndLength(decoded))
                escaped = true
            }
            return c
        }
    }
    fun isEscaped(): Boolean = escaped
    fun inVariable(): Boolean = buf != null
    fun getPos(p: Position?): Position = (p ?: Position()).also { it.buf = buf; it.bufPos = bufPos; it.posIndex = pos.index }
    fun setPos(p: Position) { buf = p.buf; bufPos = p.bufPos; pos.index = p.posIndex }
    fun skipIgnored(options: Int) {
        if (options and SKIP_WHITESPACE != 0) while (true) {
            val c = current()
            if (!PatternProps.isWhiteSpace(c)) break
            advance(UTF16.getCharCount(c))
        }
    }
    fun getCurrentBuffer(): String = buf ?: text
    fun getCurrentBufferPos(): Int = if (buf != null) bufPos else pos.index
    fun jumpahead(count: Int) {
        require(count >= 0)
        val expansion = buf
        if (expansion != null) {
            bufPos += count
            require(bufPos <= expansion.length)
            if (bufPos == expansion.length) buf = null
        } else {
            pos.index += count
            require(pos.index <= text.length)
        }
    }
    override fun toString(): String = text.substring(0, pos.index) + '|' + text.substring(pos.index)
    private fun current(): Int {
        buf?.let { return UTF16.charAt(it, bufPos) }
        return if (pos.index < text.length) UTF16.charAt(text, pos.index) else DONE
    }
    private fun advance(count: Int) {
        val expansion = buf
        if (expansion != null) { bufPos += count; if (bufPos == expansion.length) buf = null }
        else pos.index = minOf(pos.index + count, text.length)
    }
    companion object {
        const val DONE = -1
        const val PARSE_VARIABLES = 1
        const val PARSE_ESCAPES = 2
        const val SKIP_WHITESPACE = 4
    }
}
