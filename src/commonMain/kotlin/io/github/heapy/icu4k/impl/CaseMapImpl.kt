// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.Edits

/** Internal components of the ICU string casing algorithms. */
internal object CaseMapImpl {
    /** Mapping bounds and context bounds deliberately differ: context sees the whole text. */
    class StringContextIterator(
        private val text: CharSequence,
        private var cpStart: Int = 0,
        private var cpLimit: Int = 0,
    ) : UCaseProps.ContextIterator {
        private var index = 0
        private var limit = text.length
        private var direction = 0

        fun setLimit(value: Int) { limit = if (value in 0..text.length) value else text.length }
        fun moveToLimit() { cpStart = limit; cpLimit = limit }
        fun moveTo(value: Int) { cpStart = value; cpLimit = value }
        fun nextCaseMapCP(): Int {
            cpStart = cpLimit
            if (cpLimit >= limit) return -1
            val c = codePointAt(cpLimit)
            cpLimit += if (c > 0xffff) 2 else 1
            return c
        }
        fun setCPStartAndLimit(start: Int, end: Int) {
            cpStart = start; cpLimit = end; direction = 0
        }
        fun getCPStart(): Int = cpStart
        fun getCPLimit(): Int = cpLimit
        fun getCPLength(): Int = cpLimit - cpStart

        override fun reset(dir: Int) {
            direction = dir.compareTo(0)
            index = when {
                dir > 0 -> cpLimit
                dir < 0 -> cpStart
                else -> 0
            }
        }
        override fun next(): Int {
            if (direction > 0 && index < text.length) {
                val c = codePointAt(index)
                index += if (c > 0xffff) 2 else 1
                return c
            }
            if (direction < 0 && index > 0) {
                val trail = text[--index].code
                if (trail in 0xdc00..0xdfff && index > 0) {
                    val lead = text[index - 1].code
                    if (lead in 0xd800..0xdbff) {
                        --index
                        return ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
                    }
                }
                return trail
            }
            return -1
        }
        private fun codePointAt(offset: Int): Int {
            val lead = text[offset].code
            if (lead in 0xd800..0xdbff && offset + 1 < text.length) {
                val trail = text[offset + 1].code
                if (trail in 0xdc00..0xdfff) return ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
            }
            return lead
        }
    }

    const val TITLECASE_WHOLE_STRING = 0x20
    const val TITLECASE_SENTENCES = 0x40
    const val TITLECASE_ADJUST_TO_CASED = 0x400
    const val OMIT_UNCHANGED_TEXT = 0x4000

    fun addTitleAdjustmentOption(options: Int, newOption: Int): Int {
        val previous = options and 0x600
        require(previous == 0 || previous == newOption) { "multiple titlecasing index adjustment options" }
        return options or newOption
    }
    fun addTitleIteratorOption(options: Int, newOption: Int): Int {
        val previous = options and 0xe0
        require(previous == 0 || previous == newOption) { "multiple titlecasing iterator options" }
        return options or newOption
    }

    private fun appendCodePoint(dest: Appendable, c: Int): Int {
        if (c <= 0xffff) { dest.append(c.toChar()); return 1 }
        dest.append((0xd7c0 + (c shr 10)).toChar())
        dest.append((0xdc00 + (c and 0x3ff)).toChar())
        return 2
    }
    private fun appendUnchanged(src: CharSequence, start: Int, length: Int, dest: Appendable, options: Int, edits: Edits?) {
        if (length <= 0) return
        edits?.addUnchanged(length)
        if (options and OMIT_UNCHANGED_TEXT == 0) dest.append(src, start, start + length)
    }
    internal fun appendResult(result: Int, dest: Appendable, cpLength: Int, options: Int, edits: Edits?) {
        when {
            result < 0 -> {
                edits?.addUnchanged(cpLength)
                if (options and OMIT_UNCHANGED_TEXT == 0) appendCodePoint(dest, result.inv())
            }
            result <= 31 -> edits?.addReplace(cpLength, result)
            else -> {
                val length = appendCodePoint(dest, result)
                edits?.addReplace(cpLength, length)
            }
        }
    }
    private fun applyEdits(src: CharSequence, replacements: StringBuilder, edits: Edits): String {
        if (!edits.hasChanges()) return src.toString()
        val result = StringBuilder(src.length + edits.lengthDelta())
        val iter = edits.getCoarseIterator()
        while (iter.next()) {
            if (iter.hasChange()) {
                val start = iter.replacementIndex()
                result.append(replacements, start, start + iter.newLength())
            } else {
                val start = iter.sourceIndex()
                result.append(src, start, start + iter.oldLength())
            }
        }
        return result.toString()
    }
    private fun internalToLower(caseLocale: Int, options: Int, src: CharSequence, start: Int, limit: Int,
                                context: StringContextIterator?, dest: Appendable, edits: Edits?) {
        val latin = if (caseLocale == UCaseProps.LOC_ROOT ||
            (if (caseLocale >= 0) caseLocale != UCaseProps.LOC_TURKISH && caseLocale != UCaseProps.LOC_LITHUANIAN
             else options and 7 == 0)) LatinCase.TO_LOWER_NORMAL else LatinCase.TO_LOWER_TR_LT
        var iter = context
        var previous = start
        var index = start
        outer@ while (true) {
            var lead: Int
            while (true) {
                if (index >= limit) break@outer
                lead = src[index].code
                val delta: Int
                if (lead < LatinCase.LONG_S) {
                    delta = latin[lead].toInt()
                    if (delta == LatinCase.EXC) break
                    ++index
                    if (delta == 0) continue
                } else if (lead >= 0xd800) {
                    break
                } else {
                    val props = UCaseProps.getTrie().getFromU16SingleLead(lead.toChar())
                    if (UCaseProps.propsHasException(props)) break
                    ++index
                    delta = UCaseProps.getDelta(props)
                    if (!UCaseProps.isUpperOrTitleFromProps(props) || delta == 0) continue
                }
                appendUnchanged(src, previous, index - 1 - previous, dest, options, edits)
                dest.append((lead + delta).toChar())
                edits?.addReplace(1, 1)
                previous = index
            }
            val cpStart = index++
            var c = lead
            if (lead in 0xd800..0xdbff && index < limit && src[index].code in 0xdc00..0xdfff) {
                c = ((lead - 0xd800) shl 10) + src[index++].code - 0xdc00 + 0x10000
            }
            appendUnchanged(src, previous, cpStart - previous, dest, options, edits)
            previous = cpStart
            if (caseLocale >= 0) {
                if (iter == null) iter = StringContextIterator(src, cpStart, index)
                else iter.setCPStartAndLimit(cpStart, index)
                c = UCaseProps.INSTANCE.toFullLower(c, iter, dest, caseLocale)
            } else {
                c = UCaseProps.INSTANCE.toFullFolding(c, dest, options)
            }
            if (c >= 0) {
                appendResult(c, dest, index - cpStart, options, edits)
                previous = index
            }
        }
        appendUnchanged(src, previous, index - previous, dest, options, edits)
    }
    fun <A : Appendable> toLower(caseLocale: Int, options: Int, src: CharSequence, dest: A, edits: Edits?): A {
        edits?.reset()
        internalToLower(caseLocale, options, src, 0, src.length, null, dest, edits)
        return dest
    }
    fun <A : Appendable> fold(options: Int, src: CharSequence, dest: A, edits: Edits?): A {
        edits?.reset()
        internalToLower(-1, options, src, 0, src.length, null, dest, edits)
        return dest
    }
    fun toLower(caseLocale: Int, options: Int, src: CharSequence): String = lowerOrFold(caseLocale, options, src)
    fun fold(options: Int, src: CharSequence): String = lowerOrFold(-1, options, src)
    private fun lowerOrFold(locale: Int, options: Int, src: CharSequence): String {
        if (src.isEmpty()) return src.toString()
        if (src.length <= 100 && options and OMIT_UNCHANGED_TEXT == 0) {
            val edits = Edits()
            val replacements = StringBuilder()
            internalToLower(locale, options or OMIT_UNCHANGED_TEXT, src, 0, src.length, null, replacements, edits)
            return applyEdits(src, replacements, edits)
        }
        val dest = StringBuilder(src.length)
        internalToLower(locale, options, src, 0, src.length, null, dest, null)
        return dest.toString()
    }
    private fun internalToUpper(caseLocale: Int, options: Int, src: CharSequence, dest: Appendable, edits: Edits?) {
        val latin = if (caseLocale == UCaseProps.LOC_TURKISH) LatinCase.TO_UPPER_TR else LatinCase.TO_UPPER_NORMAL
        val start = 0
        val limit = src.length
        var iter: StringContextIterator? = null
        var previous = start
        var index = start
        outer@ while (true) {
            var lead: Int
            while (true) {
                if (index >= limit) break@outer
                lead = src[index].code
                val delta: Int
                if (lead < LatinCase.LONG_S) {
                    delta = latin[lead].toInt()
                    if (delta == LatinCase.EXC) break
                    ++index
                    if (delta == 0) continue
                } else if (lead >= 0xd800) {
                    break
                } else {
                    val props = UCaseProps.getTrie().getFromU16SingleLead(lead.toChar())
                    if (UCaseProps.propsHasException(props)) break
                    ++index
                    delta = UCaseProps.getDelta(props)
                    if (UCaseProps.getTypeFromProps(props) != UCaseProps.LOWER || delta == 0) continue
                }
                appendUnchanged(src, previous, index - 1 - previous, dest, options, edits)
                dest.append((lead + delta).toChar())
                edits?.addReplace(1, 1)
                previous = index
            }
            val cpStart = index++
            var c = lead
            if (lead in 0xd800..0xdbff && index < limit && src[index].code in 0xdc00..0xdfff) {
                c = ((lead - 0xd800) shl 10) + src[index++].code - 0xdc00 + 0x10000
            }
            appendUnchanged(src, previous, cpStart - previous, dest, options, edits)
            previous = cpStart
            if (iter == null) iter = StringContextIterator(src, cpStart, index)
            else iter.setCPStartAndLimit(cpStart, index)
            c = UCaseProps.INSTANCE.toFullUpper(c, iter, dest, caseLocale)
            if (c >= 0) {
                appendResult(c, dest, index - cpStart, options, edits)
                previous = index
            }
        }
        appendUnchanged(src, previous, index - previous, dest, options, edits)
    }
    fun <A : Appendable> toUpper(caseLocale: Int, options: Int, src: CharSequence, dest: A, edits: Edits?): A {
        edits?.reset()
        if (caseLocale == UCaseProps.LOC_GREEK) GreekUpper.toUpper(options, src, dest, edits)
        else internalToUpper(caseLocale, options, src, dest, edits)
        return dest
    }
    fun toUpper(caseLocale: Int, options: Int, src: CharSequence): String {
        if (src.isEmpty()) return src.toString()
        if (src.length <= 100 && options and OMIT_UNCHANGED_TEXT == 0) {
            val edits = Edits()
            val replacements = toUpper(caseLocale, options or OMIT_UNCHANGED_TEXT, src, StringBuilder(), edits)
            return applyEdits(src, replacements, edits)
        }
        return toUpper(caseLocale, options, src, StringBuilder(src.length), null).toString()
    }

    /** Boundary adapter used until the common BreakIterator hierarchy is ported. */
    interface TitleIterator {
        fun first(): Int
        fun next(): Int
    }
    private fun isLNS(c: Int): Boolean {
        val type = UCharacterProperty.INSTANCE.getType(c)
        val mask = (1 shl 1) or (1 shl 2) or (1 shl 3) or (1 shl 5) or
            (1 shl 9) or (1 shl 10) or (1 shl 11) or (1 shl 24) or
            (1 shl 25) or (1 shl 26) or (1 shl 27) or (1 shl 17)
        return (1 shl type) and mask != 0 || type == 4 && UCaseProps.INSTANCE.getType(c) != UCaseProps.NONE
    }
    fun <A : Appendable> toTitle(locale: Int, options: Int, boundaries: TitleIterator,
                                src: CharSequence, dest: A, edits: Edits?): A {
        edits?.reset()
        val iter = StringContextIterator(src)
        var previous = 0
        var first = true
        while (previous < src.length) {
            var index = if (first) { first = false; boundaries.first() } else boundaries.next()
            if (index == -1 || index > src.length) index = src.length
            if (previous < index) {
                var titleStart = previous
                iter.setLimit(index)
                var c = iter.nextCaseMapCP()
                if (options and 0x200 == 0) {
                    val toCased = options and TITLECASE_ADJUST_TO_CASED != 0
                    while (if (toCased) UCaseProps.INSTANCE.getType(c) == UCaseProps.NONE else !isLNS(c)) {
                        c = iter.nextCaseMapCP()
                        if (c < 0) break
                    }
                    titleStart = iter.getCPStart()
                    appendUnchanged(src, previous, titleStart - previous, dest, options, edits)
                }
                if (titleStart < index) {
                    c = UCaseProps.INSTANCE.toFullTitle(c, iter, dest, locale)
                    appendResult(c, dest, iter.getCPLength(), options, edits)
                    val titleLimit: Int
                    if (titleStart + 1 < index && locale == UCaseProps.LOC_DUTCH) {
                        if (c < 0) c = c.inv()
                        if (c == 0x49 || c == 0xcd) {
                            titleLimit = maybeTitleDutchIJ(src, c, titleStart + 1, index, dest, options, edits)
                            iter.moveTo(titleLimit)
                        } else titleLimit = iter.getCPLimit()
                    } else titleLimit = iter.getCPLimit()
                    if (titleLimit < index) {
                        if (options and 0x100 == 0) internalToLower(locale, options, src, titleLimit, index, iter, dest, edits)
                        else appendUnchanged(src, titleLimit, index - titleLimit, dest, options, edits)
                        iter.moveToLimit()
                    }
                }
            }
            previous = index
        }
        return dest
    }
    fun toTitle(locale: Int, options: Int, boundaries: TitleIterator, src: CharSequence): String {
        if (src.isEmpty()) return src.toString()
        if (src.length <= 100 && options and OMIT_UNCHANGED_TEXT == 0) {
            val edits = Edits()
            val replacements = toTitle(locale, options or OMIT_UNCHANGED_TEXT, boundaries, src, StringBuilder(), edits)
            return applyEdits(src, replacements, edits)
        }
        return toTitle(locale, options, boundaries, src, StringBuilder(src.length), null).toString()
    }
    private fun maybeTitleDutchIJ(src: CharSequence, c: Int, start: Int, limit: Int,
                                  dest: Appendable, options: Int, edits: Edits?): Int {
        var index = start
        var withAcute = false
        var unchanged1 = 0
        val titleJ: Boolean
        var unchanged2 = 0
        var c2 = src[index++]
        if (c == 0x49) {
            if (c2 == '\u0301') {
                withAcute = true
                unchanged1 = 1
                if (index == limit) return start
                c2 = src[index++]
            }
        } else withAcute = true
        if (c2 == 'j') titleJ = true
        else if (c2 == 'J') { titleJ = false; ++unchanged1 }
        else return start
        if (withAcute) {
            if (index == limit || src[index++] != '\u0301') return start
            if (titleJ) unchanged2 = 1 else ++unchanged1
        }
        if (index < limit) {
            val lookahead = StringContextIterator(src, index, index).nextCaseMapCP()
            val type = UCharacterProperty.INSTANCE.getType(lookahead)
            if (type == 6 || type == 7 || type == 8) return start
        }
        appendUnchanged(src, start, unchanged1, dest, options, edits)
        var position = start + unchanged1
        if (titleJ) {
            dest.append('J')
            edits?.addReplace(1, 1)
            ++position
        }
        appendUnchanged(src, position, unchanged2, dest, options, edits)
        return index
    }
}
