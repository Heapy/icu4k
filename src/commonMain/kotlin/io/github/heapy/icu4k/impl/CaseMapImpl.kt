// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

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
}
