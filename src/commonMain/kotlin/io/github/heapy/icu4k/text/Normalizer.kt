// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.UCaseProps
import io.github.heapy.icu4k.lang.UCharacter

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
    /** One side of ICU's two-level fold/decomposition comparison stack. */
    private class CompareState(var source: CharSequence) {
        var index = 0
        var level = 0
        var unit = -1
        private val sources = arrayOfNulls<CharSequence>(2)
        private val indexes = IntArray(2)
        val folded = StringBuilder()
        fun fetch() {
            if (unit >= 0) return
            while (true) {
                if (index != source.length) { unit = source[index++].code; return }
                if (level == 0) { unit = -1; return }
                do { level-- } while (sources[level] == null)
                source = sources[level]!!
                index = indexes[level]
            }
        }
        fun codePoint(): Int {
            if (unit in 0xd800..0xdbff && index != source.length && source[index].code in 0xdc00..0xdfff) {
                return 0x10000 + ((unit - 0xd800) shl 10) + source[index].code - 0xdc00
            }
            if (unit in 0xdc00..0xdfff && index >= 2 && source[index - 2].code in 0xd800..0xdbff) {
                return 0x10000 + ((source[index - 2].code - 0xd800) shl 10) + unit - 0xdc00
            }
            return unit
        }
        fun adjustSurrogates(other: CompareState) {
            if (unit in 0xd800..0xdbff) index++
            else if (unit in 0xdc00..0xdfff) {
                other.index--
                other.unit = other.source[other.index - 1].code
            }
        }
        fun push(replacement: CharSequence, decomposition: Boolean) {
            sources[level] = source
            indexes[level] = index
            level++
            if (decomposition && level < 2) { sources[level] = null; level++ }
            source = replacement
            index = 0
            unit = -1
        }
        fun orderedUnit(): Int {
            val pair = (unit <= 0xdbff && index != source.length && source[index].code in 0xdc00..0xdfff) ||
                (unit in 0xdc00..0xdfff && index != 1 && source[index - 2].code in 0xd800..0xdbff)
            return if (pair) unit else unit - 0x2800
        }
    }
    private class ArraySlice(private val chars: CharArray, private val start: Int, private val limit: Int) : CharSequence {
        init { if (start < 0 || limit < start || limit > chars.size) throw IndexOutOfBoundsException() }
        override val length: Int get() = limit - start
        override fun get(index: Int): Char {
            if (index < 0 || index >= length) throw IndexOutOfBoundsException()
            return chars[start + index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            if (startIndex < 0 || endIndex < startIndex || endIndex > length) throw IndexOutOfBoundsException()
            return ArraySlice(chars, start + startIndex, start + endIndex)
        }
        override fun toString(): String = chars.concatToString(start, limit)
    }
    private class CharsAppendable(private val chars: CharArray, private val start: Int, private val limit: Int) : Appendable {
        private var offset = start
        fun length(): Int {
            val length = offset - start
            if (offset > limit) throw IndexOutOfBoundsException(length.toString())
            return length
        }
        private fun put(c: Char) {
            if (offset < 0 || offset >= chars.size) throw IndexOutOfBoundsException()
            chars[offset++] = c
        }
        override fun append(value: Char): Appendable {
            if (offset < limit) put(value) else offset++
            return this
        }
        override fun append(value: CharSequence?): Appendable = append(value, 0, value!!.length)
        override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
            val length = endIndex - startIndex
            if (length <= limit - offset) {
                for (i in startIndex until endIndex) put(value!![i])
            } else offset += length
            return this
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
        val COMPOSE = NFC
        val COMPOSE_COMPAT = NFKC
        val DECOMP = NFD
        val DECOMP_COMPAT = NFKD
        const val IGNORE_HANGUL = 1
        const val FOLD_CASE_DEFAULT = 0
        const val FOLD_CASE_EXCLUDE_SPECIAL_I = 1
        const val INPUT_IS_FCD = 0x20000
        const val COMPARE_IGNORE_CASE = 0x10000
        const val COMPARE_CODE_POINT_ORDER = 0x8000
        const val COMPARE_NORM_OPTIONS_SHIFT = 20

        fun compose(source: String, compat: Boolean, options: Int = 0): String =
            normalize(source, if (compat) NFKC else NFC, options)
        fun decompose(source: String, compat: Boolean, options: Int = 0): String =
            normalize(source, if (compat) NFKD else NFD, options)
        fun compose(source: CharArray, target: CharArray, compat: Boolean, options: Int): Int =
            compose(source, 0, source.size, target, 0, target.size, compat, options)
        fun decompose(source: CharArray, target: CharArray, compat: Boolean, options: Int): Int =
            decompose(source, 0, source.size, target, 0, target.size, compat, options)
        fun normalize(source: CharArray, target: CharArray, mode: Mode, options: Int): Int =
            normalize(source, 0, source.size, target, 0, target.size, mode, options)
        fun compose(src: CharArray, srcStart: Int, srcLimit: Int, dest: CharArray, destStart: Int,
                    destLimit: Int, compat: Boolean, options: Int): Int =
            normalize(src, srcStart, srcLimit, dest, destStart, destLimit, if (compat) NFKC else NFC, options)
        fun decompose(src: CharArray, srcStart: Int, srcLimit: Int, dest: CharArray, destStart: Int,
                      destLimit: Int, compat: Boolean, options: Int): Int =
            normalize(src, srcStart, srcLimit, dest, destStart, destLimit, if (compat) NFKD else NFD, options)
        fun normalize(src: CharArray, srcStart: Int, srcLimit: Int, dest: CharArray, destStart: Int,
                      destLimit: Int, mode: Mode, options: Int): Int {
            val source = ArraySlice(src, srcStart, srcLimit)
            val output = CharsAppendable(dest, destStart, destLimit)
            mode.resolve(options).normalize(source, output)
            return output.length()
        }
        fun normalize(c: Int, mode: Mode, options: Int = 0): String {
            if (mode === NFD && options == 0) {
                return Normalizer2.getNFCInstance().getDecomposition(c) ?: UTF16.valueOf(c)
            }
            return normalize(UTF16.valueOf(c), mode, options)
        }
        fun quickCheck(source: CharArray, mode: Mode, options: Int): QuickCheckResult =
            quickCheck(source, 0, source.size, mode, options)
        fun quickCheck(source: CharArray, start: Int, limit: Int, mode: Mode, options: Int): QuickCheckResult =
            mode.resolve(options).quickCheck(ArraySlice(source, start, limit))
        fun isNormalized(source: CharArray, start: Int, limit: Int, mode: Mode, options: Int): Boolean =
            mode.resolve(options).isNormalized(ArraySlice(source, start, limit))
        fun isNormalized(c: Int, mode: Mode, options: Int): Boolean = isNormalized(UTF16.valueOf(c), mode, options)
        fun concatenate(left: String, right: String, mode: Mode, options: Int): String =
            mode.resolve(options).append(StringBuilder(left.length + right.length + 16).append(left), right).toString()
        fun concatenate(left: CharArray, right: CharArray, mode: Mode, options: Int): String =
            mode.resolve(options).append(StringBuilder(left.size + right.size + 16).append(left.concatToString()),
                ArraySlice(right, 0, right.size)).toString()
        fun concatenate(left: CharArray, leftStart: Int, leftLimit: Int, right: CharArray, rightStart: Int,
                        rightLimit: Int, dest: CharArray?, destStart: Int, destLimit: Int, mode: Mode, options: Int): Int {
            requireNotNull(dest)
            require(right !== dest || rightStart >= destLimit || destStart >= rightLimit) { "overlapping right and dst ranges" }
            val output = StringBuilder(leftLimit - leftStart + rightLimit - rightStart + 16)
                .append(ArraySlice(left, leftStart, leftLimit))
            mode.resolve(options).append(output, ArraySlice(right, rightStart, rightLimit))
            val length = output.length
            if (length > destLimit - destStart) throw IndexOutOfBoundsException(length.toString())
            if (destStart < 0 || destStart > dest.size - length) throw IndexOutOfBoundsException()
            for (i in 0 until length) dest[destStart + i] = output[i]
            return length
        }
        fun compare(s1: String, s2: String, options: Int): Int = internalCompare(s1, s2, options)
        fun compare(s1: CharArray, s2: CharArray, options: Int): Int =
            internalCompare(ArraySlice(s1, 0, s1.size), ArraySlice(s2, 0, s2.size), options)
        fun compare(s1: CharArray?, s1Start: Int, s1Limit: Int, s2: CharArray?, s2Start: Int,
                    s2Limit: Int, options: Int): Int {
            require(s1 != null && s1Start >= 0 && s1Limit >= s1Start &&
                s2 != null && s2Start >= 0 && s2Limit >= s2Start)
            return internalCompare(ArraySlice(s1, s1Start, s1Limit), ArraySlice(s2, s2Start, s2Limit), options)
        }
        fun compare(c1: Int, c2: Int, options: Int): Int =
            internalCompare(UTF16.valueOf(c1), UTF16.valueOf(c2), options or INPUT_IS_FCD)
        fun compare(c1: Int, s2: String, options: Int): Int = internalCompare(UTF16.valueOf(c1), s2, options)
        private const val COMPARE_EQUIV = 0x80000
        private fun internalCompare(first: CharSequence, second: CharSequence, originalOptions: Int): Int {
            var s1 = first
            var s2 = second
            val normOptions = originalOptions ushr COMPARE_NORM_OPTIONS_SHIFT
            val options = originalOptions or COMPARE_EQUIV
            if (options and INPUT_IS_FCD == 0 || options and FOLD_CASE_EXCLUDE_SPECIAL_I != 0) {
                val n2 = (if (options and FOLD_CASE_EXCLUDE_SPECIAL_I != 0) NFD else FCD).resolve(normOptions)
                val span1 = n2.spanQuickCheckYes(s1)
                val span2 = n2.spanQuickCheckYes(s2)
                if (span1 < s1.length) {
                    s1 = n2.normalizeSecondAndAppend(StringBuilder(s1.length + 16).append(s1, 0, span1),
                        s1.subSequence(span1, s1.length))
                }
                if (span2 < s2.length) {
                    s2 = n2.normalizeSecondAndAppend(StringBuilder(s2.length + 16).append(s2, 0, span2),
                        s2.subSequence(span2, s2.length))
                }
            }
            return cmpEquivFold(s1, s2, options)
        }
        /** ICU's incremental comparison core, also used by UTF16.StringComparator. */
        internal fun cmpEquivFold(first: CharSequence, second: CharSequence, options: Int): Int {
            val nfc = if (options and COMPARE_EQUIV != 0) Norm2AllModes.getNFCInstance().impl else null
            val caseProps = if (options and COMPARE_IGNORE_CASE != 0) UCaseProps.INSTANCE else null
            val a = CompareState(first)
            val b = CompareState(second)
            while (true) {
                a.fetch(); b.fetch()
                if (a.unit == b.unit) {
                    if (a.unit < 0) return 0
                    a.unit = -1; b.unit = -1
                    continue
                }
                if (a.unit < 0) return -1
                if (b.unit < 0) return 1
                val cp1 = a.codePoint()
                val cp2 = b.codePoint()
                if (a.level == 0 && caseProps != null) {
                    val length = caseProps.toFullFolding(cp1, a.folded, options)
                    if (length >= 0) {
                        a.adjustSurrogates(b)
                        if (length <= UCaseProps.MAX_STRING_LENGTH) a.folded.deleteRange(0, a.folded.length - length)
                        else { a.folded.setLength(0); UTF16.appendCodePoint(a.folded, length) }
                        a.push(a.folded, false)
                        continue
                    }
                }
                if (b.level == 0 && caseProps != null) {
                    val length = caseProps.toFullFolding(cp2, b.folded, options)
                    if (length >= 0) {
                        b.adjustSurrogates(a)
                        if (length <= UCaseProps.MAX_STRING_LENGTH) b.folded.deleteRange(0, b.folded.length - length)
                        else { b.folded.setLength(0); UTF16.appendCodePoint(b.folded, length) }
                        b.push(b.folded, false)
                        continue
                    }
                }
                if (a.level < 2 && nfc != null) {
                    val decomposition = nfc.getDecomposition(cp1)
                    if (decomposition != null) {
                        a.adjustSurrogates(b); a.push(decomposition, true)
                        continue
                    }
                }
                if (b.level < 2 && nfc != null) {
                    val decomposition = nfc.getDecomposition(cp2)
                    if (decomposition != null) {
                        b.adjustSurrogates(a); b.push(decomposition, true)
                        continue
                    }
                }
                return if (a.unit >= 0xd800 && b.unit >= 0xd800 && options and COMPARE_CODE_POINT_ORDER != 0)
                    a.orderedUnit() - b.orderedUnit() else a.unit - b.unit
            }
        }
        fun getFC_NFKC_Closure(c: Int, dest: CharArray?): Int {
            val closure = getFC_NFKC_Closure(c)
            if (closure.isNotEmpty() && dest != null && closure.length <= dest.size) {
                for (i in closure.indices) dest[i] = closure[i]
            }
            return closure.length
        }
        fun getFC_NFKC_Closure(c: Int): String {
            val modes = Norm2AllModes.getNFKCInstance()
            val nfkc = modes.comp
            val folded = StringBuilder()
            val foldedLength = UCaseProps.INSTANCE.toFullFolding(c, folded, 0)
            if (foldedLength < 0) {
                if (modes.impl.getCompQuickCheck(modes.impl.getNorm16(c)) != 0) return ""
                UTF16.appendCodePoint(folded, c)
            } else if (foldedLength > UCaseProps.MAX_STRING_LENGTH) {
                UTF16.appendCodePoint(folded, foldedLength)
            }
            val kc1 = nfkc.normalize(folded)
            val kc2 = nfkc.normalize(UCharacter.foldCase(kc1, 0))
            return if (kc1 == kc2) "" else kc2
        }
        val NO: QuickCheckResult = QuickCheckResult.no
        val YES: QuickCheckResult = QuickCheckResult.yes
        val MAYBE: QuickCheckResult = QuickCheckResult.maybe
    }
}
