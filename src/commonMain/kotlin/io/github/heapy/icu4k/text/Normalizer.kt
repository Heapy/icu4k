// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Norm2AllModes

/** Legacy normalization API being ported. Quick-check tokens are shared with [Normalizer2]. */
class Normalizer private constructor(private var text: UCharacterIterator, private var mode: Mode,
                                     private var options: Int, @Suppress("UNUSED_PARAMETER") owned: Boolean) {
    private var norm2 = mode.resolve(options)
    private var currentIndex = 0
    private var nextIndex = 0
    private val buffer = StringBuilder()
    private var bufferPos = 0

    constructor(source: String, mode: Mode, options: Int) : this(UCharacterIterator.getInstance(source), mode, options, true)
    constructor(source: CharacterIterator, mode: Mode, options: Int) : this(UCharacterIterator.getInstance(source.clone()), mode, options, true)
    constructor(source: UCharacterIterator, mode: Mode, options: Int) : this(source.clone(), mode, options, true)

    abstract class Mode protected constructor() {
        protected abstract fun getNormalizer2(options: Int): Normalizer2
        internal fun resolve(options: Int): Normalizer2 = getNormalizer2(options)
    }
    private class BuiltinMode(private val factory: () -> Normalizer2) : Mode() {
        private val standard by lazy(factory)
        private val unicode32 by lazy { FilteredNormalizer2(standard, unicode32Set) }
        override fun getNormalizer2(options: Int): Normalizer2 =
            if (options and UNICODE_3_2 != 0) unicode32 else standard
    }
    fun clone(): Normalizer = Normalizer(text.clone(), mode, options, true).also {
        it.norm2 = norm2
        it.currentIndex = currentIndex
        it.nextIndex = nextIndex
        it.buffer.append(buffer)
        it.bufferPos = bufferPos
    }
    fun current(): Int = if (bufferPos < buffer.length || nextNormalize()) codePointAt(bufferPos) else DONE
    fun next(): Int {
        if (bufferPos >= buffer.length && !nextNormalize()) return DONE
        val c = codePointAt(bufferPos)
        bufferPos += if (c > 0xffff) 2 else 1
        return c
    }
    fun previous(): Int {
        if (bufferPos <= 0 && !previousNormalize()) return DONE
        var c = buffer[bufferPos - 1].code
        if (c in 0xdc00..0xdfff && bufferPos > 1) {
            val lead = buffer[bufferPos - 2].code
            if (lead in 0xd800..0xdbff) c = 0x10000 + ((lead - 0xd800) shl 10) + c - 0xdc00
        }
        bufferPos -= if (c > 0xffff) 2 else 1
        return c
    }
    private fun codePointAt(index: Int): Int {
        val lead = buffer[index].code
        if (lead in 0xd800..0xdbff && index + 1 < buffer.length) {
            val trail = buffer[index + 1].code
            if (trail in 0xdc00..0xdfff) return 0x10000 + ((lead - 0xd800) shl 10) + trail - 0xdc00
        }
        return lead
    }
    fun reset() { text.setToStart(); currentIndex = 0; nextIndex = 0; clearBuffer() }
    fun setIndexOnly(index: Int) { text.setIndex(index); currentIndex = index; nextIndex = index; clearBuffer() }
    fun setIndex(index: Int): Int { setIndexOnly(index); return current() }
    fun getBeginIndex(): Int = 0
    fun getEndIndex(): Int = endIndex()
    fun startIndex(): Int = 0
    fun endIndex(): Int = text.getLength()
    fun getIndex(): Int = if (bufferPos < buffer.length) currentIndex else nextIndex
    fun first(): Int { reset(); return next() }
    fun last(): Int {
        text.setToLimit(); currentIndex = text.getIndex(); nextIndex = currentIndex
        clearBuffer(); return previous()
    }
    fun setMode(newMode: Mode) { mode = newMode; norm2 = mode.resolve(options) }
    fun getMode(): Mode = mode
    fun setOption(option: Int, value: Boolean) {
        options = if (value) options or option else options and option.inv()
        norm2 = mode.resolve(options)
    }
    fun getOption(option: Int): Int = if (options and option != 0) 1 else 0
    fun getText(fillIn: CharArray): Int = text.getText(fillIn)
    fun getText(): String = text.getText()
    fun getLength(): Int = text.getLength()
    fun setText(source: String) { text = UCharacterIterator.getInstance(source); reset() }
    fun setText(source: StringBuilder) { text = UCharacterIterator.getInstance(source); reset() }
    fun setText(source: CharArray) { text = UCharacterIterator.getInstance(source); reset() }
    fun setText(source: CharacterIterator) { text = UCharacterIterator.getInstance(source); reset() }
    fun setText(source: UCharacterIterator) { text = source.clone(); reset() }
    private fun clearBuffer() { buffer.setLength(0); bufferPos = 0 }
    private fun nextNormalize(): Boolean {
        clearBuffer(); currentIndex = nextIndex; text.setIndex(nextIndex)
        var c = text.nextCodePoint()
        if (c < 0) return false
        val segment = StringBuilder()
        UTF16.appendCodePoint(segment, c)
        while (true) {
            c = text.nextCodePoint()
            if (c < 0) break
            if (norm2.hasBoundaryBefore(c)) { text.moveCodePointIndex(-1); break }
            UTF16.appendCodePoint(segment, c)
        }
        nextIndex = text.getIndex()
        norm2.normalize(segment, buffer)
        return buffer.isNotEmpty()
    }
    private fun previousNormalize(): Boolean {
        clearBuffer(); nextIndex = currentIndex; text.setIndex(currentIndex)
        val segment = StringBuilder()
        while (true) {
            val c = text.previousCodePoint()
            if (c < 0) break
            segment.insert(0, UTF16.valueOf(c))
            if (norm2.hasBoundaryBefore(c)) break
        }
        currentIndex = text.getIndex()
        norm2.normalize(segment, buffer)
        bufferPos = buffer.length
        return buffer.isNotEmpty()
    }

    class QuickCheckResult private constructor() {
        companion object {
            internal val no = QuickCheckResult()
            internal val yes = QuickCheckResult()
            internal val maybe = QuickCheckResult()
        }
    }
    companion object {
        const val UNICODE_3_2 = 0x20
        const val DONE = UCharacterIterator.DONE
        private val unicode32Set by lazy { UnicodeSet().applyPropertyAlias("age", "3.2").freeze() }
        val NONE: Mode = object : Mode() { override fun getNormalizer2(options: Int) = Norm2AllModes.NOOP_NORMALIZER2 }
        val NFD: Mode = BuiltinMode { Normalizer2.getNFDInstance() }
        val NFKD: Mode = BuiltinMode { Normalizer2.getNFKDInstance() }
        val NFC: Mode = BuiltinMode { Normalizer2.getNFCInstance() }
        val NFKC: Mode = BuiltinMode { Normalizer2.getNFKCInstance() }
        val FCD: Mode = BuiltinMode { Norm2AllModes.getFCDNormalizer2() }
        val DEFAULT = NFC
        val NO_OP = NONE
        fun normalize(source: String, mode: Mode, options: Int = 0): String = mode.resolve(options).normalize(source)
        fun quickCheck(source: String, mode: Mode, options: Int = 0): QuickCheckResult = mode.resolve(options).quickCheck(source)
        fun isNormalized(source: String, mode: Mode, options: Int = 0): Boolean = mode.resolve(options).isNormalized(source)
        val NO: QuickCheckResult = QuickCheckResult.no
        val YES: QuickCheckResult = QuickCheckResult.yes
        val MAYBE: QuickCheckResult = QuickCheckResult.maybe
    }
}
