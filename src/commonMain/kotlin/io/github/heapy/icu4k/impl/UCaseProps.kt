// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** Unicode case tables, full mappings, context rules and closure algorithms. */
class UCaseProps internal constructor(bytes: BinaryBuffer = ICUBinary.getRequiredData("ucase.icu")) {
    private val trie: Trie2_16
    private val exceptions: String
    private val unfold: CharArray
    init {
        ICUBinary.readHeader(bytes, 0x63415345, ICUBinary.Authenticate { it[0] == 4.toByte() })
        val count = bytes.readInt()
        if (count < 16 || count - 1 > bytes.remaining / 4) throw ICUUncheckedIOException("indexes[0] too small in ucase.icu")
        val indexes = IntArray(count)
        indexes[0] = count
        for (i in 1 until count) indexes[i] = bytes.readInt()
        trie = Trie2_16.createFromSerialized(bytes)
        val trieLength = trie.getSerializedLength()
        if (trieLength > indexes[2]) throw ICUUncheckedIOException("ucase.icu: not enough bytes for the trie")
        bytes.skip(indexes[2] - trieLength)
        exceptions = bytes.readChars(indexes[3]).concatToString()
        unfold = bytes.readChars(indexes[4])
    }

    private fun getSlotValue(excWord: Int, index: Int, excOffset: Int): Int {
        var offset = excOffset
        val slot = (excWord and ((1 shl index) - 1)).countOneBits()
        if ((excWord and EXC_DOUBLE_SLOTS) == 0) return exceptions[offset + slot].code
        offset += 2 * slot
        return (exceptions[offset].code shl 16) or exceptions[offset + 1].code
    }
    private fun getSlotValueAndOffset(excWord: Int, index: Int, excOffset: Int): Long {
        val slot = (excWord and ((1 shl index) - 1)).countOneBits()
        val offset = excOffset + if ((excWord and EXC_DOUBLE_SLOTS) == 0) slot else 2 * slot + 1
        return (getSlotValue(excWord, index, excOffset).toLong() and 0xffffffffL) or (offset.toLong() shl 32)
    }
    private fun deltaMapping(c: Int, excWord: Int, offset: Int): Int {
        val delta = getSlotValue(excWord, EXC_DELTA, offset)
        return if ((excWord and EXC_DELTA_IS_NEGATIVE) == 0) c + delta else c - delta
    }

    fun tolower(c: Int): Int {
        val props = trie[c]
        if (!propsHasException(props)) return if (isUpperOrTitleFromProps(props)) c + getDelta(props) else c
        val offset = props shr EXC_SHIFT
        val word = exceptions[offset].code
        if (hasSlot(word, EXC_DELTA) && isUpperOrTitleFromProps(props)) return deltaMapping(c, word, offset + 1)
        return if (hasSlot(word, EXC_LOWER)) getSlotValue(word, EXC_LOWER, offset + 1) else c
    }
    fun toupper(c: Int): Int = simpleUpperOrTitle(c, false)
    fun totitle(c: Int): Int = simpleUpperOrTitle(c, true)
    private fun simpleUpperOrTitle(c: Int, title: Boolean): Int {
        val props = trie[c]
        if (!propsHasException(props)) return if (getTypeFromProps(props) == LOWER) c + getDelta(props) else c
        val offset = props shr EXC_SHIFT
        val word = exceptions[offset].code
        if (hasSlot(word, EXC_DELTA) && getTypeFromProps(props) == LOWER) return deltaMapping(c, word, offset + 1)
        val slot = if (title && hasSlot(word, EXC_TITLE)) EXC_TITLE else if (hasSlot(word, EXC_UPPER)) EXC_UPPER else return c
        return getSlotValue(word, slot, offset + 1)
    }
    fun getType(c: Int): Int = getTypeFromProps(trie[c])
    fun getTypeOrIgnorable(c: Int): Int = trie[c] and 7
    fun getDotType(c: Int): Int {
        val props = trie[c]
        return if (!propsHasException(props)) props and DOT_MASK else (exceptions[props shr EXC_SHIFT].code shr EXC_DOT_SHIFT) and DOT_MASK
    }
    fun isSoftDotted(c: Int): Boolean = getDotType(c) == SOFT_DOTTED
    fun isCaseSensitive(c: Int): Boolean {
        val props = trie[c]
        return if (!propsHasException(props)) (props and SENSITIVE) != 0 else (exceptions[props shr EXC_SHIFT].code and EXC_SENSITIVE) != 0
    }

    fun fold(c: Int, options: Int): Int {
        val props = trie[c]
        if (!propsHasException(props)) return if (isUpperOrTitleFromProps(props)) c + getDelta(props) else c
        val offset = props shr EXC_SHIFT
        val word = exceptions[offset].code
        if ((word and EXC_CONDITIONAL_FOLD) != 0) {
            if ((options and FOLD_CASE_OPTIONS_MASK) == 0) {
                if (c == 0x49) return 0x69
                if (c == 0x130) return c
            } else {
                if (c == 0x49) return 0x131
                if (c == 0x130) return 0x69
            }
        }
        if ((word and EXC_NO_SIMPLE_CASE_FOLDING) != 0) return c
        if (hasSlot(word, EXC_DELTA) && isUpperOrTitleFromProps(props)) return deltaMapping(c, word, offset + 1)
        val slot = if (hasSlot(word, EXC_FOLD)) EXC_FOLD else if (hasSlot(word, EXC_LOWER)) EXC_LOWER else return c
        return getSlotValue(word, slot, offset + 1)
    }

    /** ICU protocol: negative means unchanged (~c); 0..31 is appended string length; otherwise a code point. */
    fun toFullFolding(c: Int, out: Appendable, options: Int): Int {
        val props = trie[c]
        var result = c
        if (!propsHasException(props)) {
            if (isUpperOrTitleFromProps(props)) result = c + getDelta(props)
        } else {
            var offset = props shr EXC_SHIFT
            val word = exceptions[offset++].code
            val offset0 = offset
            if ((word and EXC_CONDITIONAL_FOLD) != 0) {
                if ((options and FOLD_CASE_OPTIONS_MASK) == 0) {
                    if (c == 0x49) return 0x69
                    if (c == 0x130) { out.append("i\u0307"); return 2 }
                } else {
                    if (c == 0x49) return 0x131
                    if (c == 0x130) return 0x69
                }
            } else if (hasSlot(word, EXC_FULL_MAPPINGS)) {
                val slot = getSlotValueAndOffset(word, EXC_FULL_MAPPINGS, offset)
                val full = slot.toInt() and 0xffff
                offset = (slot shr 32).toInt() + 1 + (full and FULL_LOWER)
                val length = (full shr 4) and 15
                if (length != 0) { out.append(exceptions, offset, offset + length); return length }
            }
            if ((word and EXC_NO_SIMPLE_CASE_FOLDING) != 0) return c.inv()
            if (hasSlot(word, EXC_DELTA) && isUpperOrTitleFromProps(props)) return deltaMapping(c, word, offset0)
            val mapping = if (hasSlot(word, EXC_FOLD)) EXC_FOLD else if (hasSlot(word, EXC_LOWER)) EXC_LOWER else return c.inv()
            result = getSlotValue(word, mapping, offset0)
        }
        return if (result == c) result.inv() else result
    }

    // Callback forms keep these algorithms independent of UnicodeSet while it is ported.
    internal fun addCaseClosure(c: Int, addCodePoint: (Int) -> Unit, addString: (String) -> Unit) =
        addClosure(c, false, addCodePoint, addString)
    internal fun addSimpleCaseClosure(c: Int, addCodePoint: (Int) -> Unit) =
        addClosure(c, true, addCodePoint) { }

    private fun addClosure(c: Int, simple: Boolean, addCodePoint: (Int) -> Unit, addString: (String) -> Unit) {
        val props = trie[c]
        if (!propsHasException(props)) {
            if (getTypeFromProps(props) != NONE) {
                val delta = getDelta(props)
                if (delta != 0) addCodePoint(c + delta)
            }
            return
        }
        val offset = (props shr EXC_SHIFT) + 1
        val word = exceptions[offset - 1].code
        if ((word and EXC_CONDITIONAL_FOLD) != 0) {
            if (c == 0x49) { addCodePoint(0x69); return }
            if (c == 0x130) { if (!simple) addString("i\u0307"); return }
        } else if (c == 0x69) {
            addCodePoint(0x49)
            return
        } else if (c == 0x131) {
            return
        }
        for (slot in EXC_LOWER..EXC_TITLE) if (hasSlot(word, slot)) addCodePoint(getSlotValue(word, slot, offset))
        if (hasSlot(word, EXC_DELTA)) addCodePoint(deltaMapping(c, word, offset))
        var closureLength = 0
        var closureOffset = 0
        if (hasSlot(word, EXC_CLOSURE)) {
            val value = getSlotValueAndOffset(word, EXC_CLOSURE, offset)
            closureLength = value.toInt() and CLOSURE_MAX_LENGTH
            closureOffset = (value shr 32).toInt() + 1
        }
        if ((!simple || closureLength > 0) && hasSlot(word, EXC_FULL_MAPPINGS)) {
            val value = getSlotValueAndOffset(word, EXC_FULL_MAPPINGS, offset)
            var full = value.toInt() and 0xffff
            var start = (value shr 32).toInt() + 1
            start += full and FULL_LOWER
            full = full shr 4
            val foldingLength = full and 15
            if (!simple && foldingLength != 0) addString(exceptions.substring(start, start + foldingLength))
            start += foldingLength
            full = full shr 4
            start += full and 15
            full = full shr 4
            start += full
            closureOffset = start
        }
        var index = closureOffset
        val limit = index + closureLength
        while (index < limit) {
            val mapping = UTF16.charAt(exceptions, index)
            addCodePoint(mapping)
            index += UTF16.getCharCount(c)
        }
    }

    internal fun addStringCaseClosure(s: String?, addCodePoint: (Int) -> Unit, addString: (String) -> Unit): Boolean {
        if (unfold.isEmpty() || s == null || s.length <= 1) return false
        val rows = unfold[0].code
        val rowWidth = unfold[1].code
        val stringWidth = unfold[2].code
        if (s.length > stringWidth) return false
        var start = 0
        var limit = rows
        while (start < limit) {
            val row = (start + limit) / 2
            val offset = (row + 1) * rowWidth
            val comparison = strcmpMax(s, offset, stringWidth)
            when {
                comparison < 0 -> limit = row
                comparison > 0 -> start = row + 1
                else -> {
                    var i = stringWidth
                    while (i < rowWidth && unfold[offset + i].code != 0) {
                        val cp = UTF16.charAt(unfold, offset, unfold.size, i)
                        addCodePoint(cp)
                        addCaseClosure(cp, addCodePoint, addString)
                        i += UTF16.getCharCount(cp)
                    }
                    return true
                }
            }
        }
        return false
    }
    private fun strcmpMax(s: String, offset: Int, max: Int): Int {
        for (i in s.indices) {
            val c = unfold[offset + i].code
            if (c == 0) return 1
            val difference = s[i].code - c
            if (difference != 0) return difference
        }
        return if (max == s.length || unfold[offset + s.length].code == 0) 0 else s.length - max
    }

    interface ContextIterator {
        /** Negative direction reads before the character; positive reads after it. */
        fun reset(dir: Int)
        fun next(): Int
    }

    private fun isFollowedByCasedLetter(iter: ContextIterator?, direction: Int): Boolean {
        if (iter == null) return false
        iter.reset(direction)
        while (true) {
            val c = iter.next()
            if (c < 0) return false
            val type = getTypeOrIgnorable(c)
            if ((type and IGNORABLE) == 0) return type != NONE
        }
    }
    private fun isPrecededBySoftDotted(iter: ContextIterator?): Boolean {
        if (iter == null) return false
        iter.reset(-1)
        while (true) {
            val c = iter.next()
            if (c < 0) return false
            val dot = getDotType(c)
            if (dot == SOFT_DOTTED) return true
            if (dot != OTHER_ACCENT) return false
        }
    }
    private fun isPrecededByI(iter: ContextIterator?): Boolean {
        if (iter == null) return false
        iter.reset(-1)
        while (true) {
            val c = iter.next()
            if (c < 0) return false
            if (c == 0x49) return true
            if (getDotType(c) != OTHER_ACCENT) return false
        }
    }
    private fun isFollowedByMoreAbove(iter: ContextIterator?): Boolean {
        if (iter == null) return false
        iter.reset(1)
        while (true) {
            val c = iter.next()
            if (c < 0) return false
            val dot = getDotType(c)
            if (dot == ABOVE) return true
            if (dot != OTHER_ACCENT) return false
        }
    }
    private fun isFollowedByDotAbove(iter: ContextIterator?): Boolean {
        if (iter == null) return false
        iter.reset(1)
        while (true) {
            val c = iter.next()
            if (c < 0) return false
            if (c == 0x307) return true
            if (getDotType(c) != OTHER_ACCENT) return false
        }
    }

    fun toFullLower(c: Int, iter: ContextIterator?, out: Appendable, caseLocale: Int): Int {
        val props = trie[c]
        var result = c
        if (!propsHasException(props)) {
            if (isUpperOrTitleFromProps(props)) result = c + getDelta(props)
        } else {
            val offset = (props shr EXC_SHIFT) + 1
            val word = exceptions[offset - 1].code
            if ((word and EXC_CONDITIONAL_SPECIAL) != 0) {
                if (caseLocale == LOC_LITHUANIAN &&
                    (((c == 0x49 || c == 0x4a || c == 0x12e) && isFollowedByMoreAbove(iter)) || c == 0xcc || c == 0xcd || c == 0x128)) {
                    val mapping = when (c) {
                        0x49 -> "i\u0307"
                        0x4a -> "j\u0307"
                        0x12e -> "\u012f\u0307"
                        0xcc -> "i\u0307\u0300"
                        0xcd -> "i\u0307\u0301"
                        0x128 -> "i\u0307\u0303"
                        else -> ""
                    }
                    out.append(mapping)
                    return mapping.length
                } else if (caseLocale == LOC_TURKISH && c == 0x130) {
                    return 0x69
                } else if (caseLocale == LOC_TURKISH && c == 0x307 && isPrecededByI(iter)) {
                    return 0
                } else if (caseLocale == LOC_TURKISH && c == 0x49 && !isFollowedByDotAbove(iter)) {
                    return 0x131
                } else if (c == 0x130) {
                    out.append("i\u0307")
                    return 2
                } else if (c == 0x3a3 && !isFollowedByCasedLetter(iter, 1) && isFollowedByCasedLetter(iter, -1)) {
                    return 0x3c2
                }
            } else if (hasSlot(word, EXC_FULL_MAPPINGS)) {
                val slot = getSlotValueAndOffset(word, EXC_FULL_MAPPINGS, offset)
                val length = slot.toInt() and FULL_LOWER
                if (length != 0) {
                    val start = (slot shr 32).toInt() + 1
                    out.append(exceptions, start, start + length)
                    return length
                }
            }
            if (hasSlot(word, EXC_DELTA) && isUpperOrTitleFromProps(props)) return deltaMapping(c, word, offset)
            if (hasSlot(word, EXC_LOWER)) result = getSlotValue(word, EXC_LOWER, offset)
        }
        return if (result == c) result.inv() else result
    }

    private fun toUpperOrTitle(c: Int, iter: ContextIterator?, out: Appendable, locale: Int, upper: Boolean): Int {
        val props = trie[c]
        var result = c
        if (!propsHasException(props)) {
            if (getTypeFromProps(props) == LOWER) result = c + getDelta(props)
        } else {
            val offset = (props shr EXC_SHIFT) + 1
            val word = exceptions[offset - 1].code
            if ((word and EXC_CONDITIONAL_SPECIAL) != 0) {
                if (locale == LOC_TURKISH && c == 0x69) {
                    return 0x130
                } else if (locale == LOC_LITHUANIAN && c == 0x307 && isPrecededBySoftDotted(iter)) {
                    return 0
                } else if (c == 0x587) {
                    out.append(if (locale == LOC_ARMENIAN) {
                        if (upper) "\u0535\u054e" else "\u0535\u057e"
                    } else {
                        if (upper) "\u0535\u0552" else "\u0535\u0582"
                    })
                    return 2
                }
            } else if (hasSlot(word, EXC_FULL_MAPPINGS)) {
                val slot = getSlotValueAndOffset(word, EXC_FULL_MAPPINGS, offset)
                var full = slot.toInt() and 0xffff
                var start = (slot shr 32).toInt() + 1
                start += full and FULL_LOWER
                full = full shr 4
                start += full and 15
                full = full shr 4
                if (upper) {
                    full = full and 15
                } else {
                    start += full and 15
                    full = (full shr 4) and 15
                }
                if (full != 0) { out.append(exceptions, start, start + full); return full }
            }
            if (hasSlot(word, EXC_DELTA) && getTypeFromProps(props) == LOWER) return deltaMapping(c, word, offset)
            val mapping = if (!upper && hasSlot(word, EXC_TITLE)) EXC_TITLE else if (hasSlot(word, EXC_UPPER)) EXC_UPPER else return c.inv()
            result = getSlotValue(word, mapping, offset)
        }
        return if (result == c) result.inv() else result
    }
    fun toFullUpper(c: Int, iter: ContextIterator?, out: Appendable, caseLocale: Int): Int = toUpperOrTitle(c, iter, out, caseLocale, true)
    fun toFullTitle(c: Int, iter: ContextIterator?, out: Appendable, caseLocale: Int): Int = toUpperOrTitle(c, iter, out, caseLocale, false)

    fun hasBinaryProperty(c: Int, which: Int): Boolean = when (which) {
        UProperty.LOWERCASE -> getType(c) == LOWER
        UProperty.UPPERCASE -> getType(c) == UPPER
        UProperty.SOFT_DOTTED -> isSoftDotted(c)
        UProperty.CASE_SENSITIVE -> isCaseSensitive(c)
        UProperty.CASED -> getType(c) != NONE
        UProperty.CASE_IGNORABLE -> (getTypeOrIgnorable(c) shr 2) != 0
        UProperty.CHANGES_WHEN_LOWERCASED -> toFullLower(c, null, propertyOutput, LOC_ROOT) >= 0
        UProperty.CHANGES_WHEN_UPPERCASED -> toFullUpper(c, null, propertyOutput, LOC_ROOT) >= 0
        UProperty.CHANGES_WHEN_TITLECASED -> toFullTitle(c, null, propertyOutput, LOC_ROOT) >= 0
        UProperty.CHANGES_WHEN_CASEMAPPED -> {
            val output = propertyOutput
            toFullLower(c, null, output, LOC_ROOT) >= 0 || toFullUpper(c, null, output, LOC_ROOT) >= 0 || toFullTitle(c, null, output, LOC_ROOT) >= 0
        }
        else -> false
    }

    internal fun forEachPropertyStart(add: (Int) -> Unit) {
        for (range in trie) {
            if (range.leadSurrogate) break
            add(range.startCodePoint)
        }
    }

    companion object {
        // Property queries only need the return protocol, not the emitted text.
        // An immutable sink avoids both temporary buffers and ICU4J's shared mutable buffer.
        private val propertyOutput = object : Appendable {
            override fun append(value: Char): Appendable = this
            override fun append(value: CharSequence?): Appendable = this
            override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable = this
        }
        val INSTANCE: UCaseProps by lazy { UCaseProps() }
        const val TYPE_MASK = 3
        const val NONE = 0
        const val LOWER = 1
        const val UPPER = 2
        const val TITLE = 3
        const val MAX_STRING_LENGTH = 0x1f
        const val LOC_ROOT = 1
        internal const val LOC_TURKISH = 2
        internal const val LOC_LITHUANIAN = 3
        internal const val LOC_GREEK = 4
        const val LOC_DUTCH = 5
        internal const val LOC_ARMENIAN = 6
        internal const val FOLD_CASE_OPTIONS_MASK = 7
        internal const val IGNORABLE = 4
        private const val EXCEPTION = 8
        private const val SENSITIVE = 0x10
        private const val DOT_MASK = 0x60
        private const val SOFT_DOTTED = 0x20
        private const val ABOVE = 0x40
        private const val OTHER_ACCENT = 0x60
        private const val EXC_SHIFT = 4
        private const val EXC_LOWER = 0
        private const val EXC_FOLD = 1
        private const val EXC_UPPER = 2
        private const val EXC_TITLE = 3
        private const val EXC_DELTA = 4
        private const val EXC_CLOSURE = 6
        private const val EXC_FULL_MAPPINGS = 7
        private const val EXC_DOUBLE_SLOTS = 0x100
        private const val EXC_NO_SIMPLE_CASE_FOLDING = 0x200
        private const val EXC_DELTA_IS_NEGATIVE = 0x400
        private const val EXC_SENSITIVE = 0x800
        private const val EXC_DOT_SHIFT = 7
        private const val EXC_CONDITIONAL_SPECIAL = 0x4000
        private const val EXC_CONDITIONAL_FOLD = 0x8000
        private const val FULL_LOWER = 15
        private const val CLOSURE_MAX_LENGTH = 15
        internal fun getTrie(): Trie2_16 = INSTANCE.trie
        internal fun propsHasException(props: Int): Boolean = (props and EXCEPTION) != 0
        internal fun getTypeFromProps(props: Int): Int = props and TYPE_MASK
        internal fun isUpperOrTitleFromProps(props: Int): Boolean = (props and 2) != 0
        internal fun getDelta(props: Int): Int = props.toShort().toInt() shr 7
        private fun hasSlot(flags: Int, index: Int): Boolean = (flags and (1 shl index)) != 0
        internal fun getCaseLocale(language: String): Int = when (language) {
            "tr", "az", "tur", "aze" -> LOC_TURKISH
            "lt", "lit" -> LOC_LITHUANIAN
            "el", "ell" -> LOC_GREEK
            "nl", "nld" -> LOC_DUTCH
            "hy", "hye" -> LOC_ARMENIAN
            else -> LOC_ROOT
        }
    }
}
