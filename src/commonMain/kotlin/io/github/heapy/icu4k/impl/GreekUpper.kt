// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.Edits

/** ICU Greek uppercase tables and context state machine. */
internal object GreekUpper {
    private const val UPPER_MASK = 0x3ff
    private const val HAS_VOWEL = 0x1000
    private const val HAS_YPOGEGRAMMENI = 0x2000
    private const val HAS_ACCENT = 0x4000
    private const val HAS_DIALYTIKA = 0x8000
    private const val HAS_COMBINING_DIALYTIKA = 0x10000
    private const val HAS_OTHER_GREEK_DIACRITIC = 0x20000
    private const val HAS_VOWEL_AND_ACCENT = HAS_VOWEL or HAS_ACCENT
    private const val HAS_VOWEL_AND_ACCENT_AND_DIALYTIKA = HAS_VOWEL_AND_ACCENT or HAS_DIALYTIKA
    private const val HAS_EITHER_DIALYTIKA = HAS_DIALYTIKA or HAS_COMBINING_DIALYTIKA
    private const val AFTER_CASED = 1
    private const val AFTER_VOWEL_WITH_COMBINING_ACCENT = 2
    private const val AFTER_VOWEL_WITH_PRECOMPOSED_ACCENT = 4
    private val data0370 = intArrayOf(
        0x0370,
        0x0370,
        0x0372,
        0x0372,
        0,
        0,
        0x0376,
        0x0376,
        0,
        0,
        0x037A,
        0x03FD,
        0x03FE,
        0x03FF,
        0,
        0x037F,
        0,
        0,
        0,
        0,
        0,
        0,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT or HAS_DIALYTIKA,
        0x0391 or HAS_VOWEL,
        0x0392,
        0x0393,
        0x0394,
        0x0395 or HAS_VOWEL,
        0x0396,
        0x0397 or HAS_VOWEL,
        0x0398,
        0x0399 or HAS_VOWEL,
        0x039A,
        0x039B,
        0x039C,
        0x039D,
        0x039E,
        0x039F or HAS_VOWEL,
        0x03A0,
        0x03A1,
        0,
        0x03A3,
        0x03A4,
        0x03A5 or HAS_VOWEL,
        0x03A6,
        0x03A7,
        0x03A8,
        0x03A9 or HAS_VOWEL,
        0x0399 or HAS_VOWEL or HAS_DIALYTIKA,
        0x03A5 or HAS_VOWEL or HAS_DIALYTIKA,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT or HAS_DIALYTIKA,
        0x0391 or HAS_VOWEL,
        0x0392,
        0x0393,
        0x0394,
        0x0395 or HAS_VOWEL,
        0x0396,
        0x0397 or HAS_VOWEL,
        0x0398,
        0x0399 or HAS_VOWEL,
        0x039A,
        0x039B,
        0x039C,
        0x039D,
        0x039E,
        0x039F or HAS_VOWEL,
        0x03A0,
        0x03A1,
        0x03A3,
        0x03A3,
        0x03A4,
        0x03A5 or HAS_VOWEL,
        0x03A6,
        0x03A7,
        0x03A8,
        0x03A9 or HAS_VOWEL,
        0x0399 or HAS_VOWEL or HAS_DIALYTIKA,
        0x03A5 or HAS_VOWEL or HAS_DIALYTIKA,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03CF,
        0x0392,
        0x0398,
        0x03D2,
        0x03D2 or HAS_ACCENT,
        0x03D2 or HAS_DIALYTIKA,
        0x03A6,
        0x03A0,
        0x03CF,
        0x03D8,
        0x03D8,
        0x03DA,
        0x03DA,
        0x03DC,
        0x03DC,
        0x03DE,
        0x03DE,
        0x03E0,
        0x03E0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0x039A,
        0x03A1,
        0x03F9,
        0x037F,
        0x03F4,
        0x0395 or HAS_VOWEL,
        0,
        0x03F7,
        0x03F7,
        0x03F9,
        0x03FA,
        0x03FA,
        0x03FC,
        0x03FD,
        0x03FE,
        0x03FF,
    )
    private val data1F00 = intArrayOf(
        0x0391 or HAS_VOWEL,
        0x0391 or HAS_VOWEL,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL,
        0x0391 or HAS_VOWEL,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL,
        0x0395 or HAS_VOWEL,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0,
        0,
        0x0395 or HAS_VOWEL,
        0x0395 or HAS_VOWEL,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0,
        0,
        0x0397 or HAS_VOWEL,
        0x0397 or HAS_VOWEL,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL,
        0x0397 or HAS_VOWEL,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL,
        0x0399 or HAS_VOWEL,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL,
        0x0399 or HAS_VOWEL,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL,
        0x039F or HAS_VOWEL,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0,
        0,
        0x039F or HAS_VOWEL,
        0x039F or HAS_VOWEL,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0,
        0,
        0x03A5 or HAS_VOWEL,
        0x03A5 or HAS_VOWEL,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0,
        0x03A5 or HAS_VOWEL,
        0,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL,
        0x03A9 or HAS_VOWEL,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL,
        0x03A9 or HAS_VOWEL,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0,
        0,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL,
        0x0391 or HAS_VOWEL,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0391 or HAS_VOWEL,
        0x0391 or HAS_VOWEL,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_ACCENT,
        0x0391 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0,
        0x0399 or HAS_VOWEL,
        0,
        0,
        0,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0395 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_ACCENT,
        0x0397 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0,
        0,
        0,
        0x0399 or HAS_VOWEL,
        0x0399 or HAS_VOWEL,
        0x0399 or HAS_VOWEL or HAS_ACCENT or HAS_DIALYTIKA,
        0x0399 or HAS_VOWEL or HAS_ACCENT or HAS_DIALYTIKA,
        0,
        0,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT or HAS_DIALYTIKA,
        0x0399 or HAS_VOWEL,
        0x0399 or HAS_VOWEL,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0x0399 or HAS_VOWEL or HAS_ACCENT,
        0,
        0,
        0,
        0,
        0x03A5 or HAS_VOWEL,
        0x03A5 or HAS_VOWEL,
        0x03A5 or HAS_VOWEL or HAS_ACCENT or HAS_DIALYTIKA,
        0x03A5 or HAS_VOWEL or HAS_ACCENT or HAS_DIALYTIKA,
        0x03A1,
        0x03A1,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT or HAS_DIALYTIKA,
        0x03A5 or HAS_VOWEL,
        0x03A5 or HAS_VOWEL,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A5 or HAS_VOWEL or HAS_ACCENT,
        0x03A1,
        0,
        0,
        0,
        0,
        0,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x039F or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_ACCENT,
        0x03A9 or HAS_VOWEL or HAS_YPOGEGRAMMENI,
        0,
        0,
        0,
    )
    private fun getLetterData(c: Int): Int = when {
        c < 0x370 || c > 0x2126 || c in 0x400..0x1eff -> 0
        c <= 0x3ff -> data0370[c - 0x370]
        c <= 0x1fff -> data1F00[c - 0x1f00]
        c == 0x2126 -> 0x3a9 or HAS_VOWEL
        else -> 0
    }
    private fun getDiacriticData(c: Int): Int = when (c) {
        0x300, 0x301, 0x342, 0x302, 0x303, 0x311 -> HAS_ACCENT
        0x308 -> HAS_COMBINING_DIALYTIKA
        0x344 -> HAS_COMBINING_DIALYTIKA or HAS_ACCENT
        0x345 -> HAS_YPOGEGRAMMENI
        0x304, 0x306, 0x313, 0x314, 0x343 -> HAS_OTHER_GREEK_DIACRITIC
        else -> 0
    }
    private fun codePointAt(src: CharSequence, index: Int): Int {
        val lead = src[index].code
        if (lead in 0xd800..0xdbff && index + 1 < src.length) {
            val trail = src[index + 1].code
            if (trail in 0xdc00..0xdfff) return ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
        }
        return lead
    }
    private fun isFollowedByCasedLetter(src: CharSequence, start: Int): Boolean {
        var index = start
        while (index < src.length) {
            val c = codePointAt(src, index)
            val type = UCaseProps.INSTANCE.getTypeOrIgnorable(c)
            if (type and UCaseProps.IGNORABLE == 0) return type != UCaseProps.NONE
            index += if (c > 0xffff) 2 else 1
        }
        return false
    }
    fun toUpper(options: Int, src: CharSequence, dest: Appendable, edits: Edits?) {
        var state = 0
        var index = 0
        while (index < src.length) {
            var c = codePointAt(src, index)
            var nextIndex = index + if (c > 0xffff) 2 else 1
            var nextState = 0
            val type = UCaseProps.INSTANCE.getTypeOrIgnorable(c)
            if (type and UCaseProps.IGNORABLE != 0) nextState = nextState or (state and AFTER_CASED)
            else if (type != UCaseProps.NONE) nextState = nextState or AFTER_CASED
            var data = getLetterData(c)
            if (data > 0) {
                var upper = data and UPPER_MASK
                if (data and HAS_VOWEL != 0 &&
                    state and (AFTER_VOWEL_WITH_PRECOMPOSED_ACCENT or AFTER_VOWEL_WITH_COMBINING_ACCENT) != 0 &&
                    (upper == 0x399 || upper == 0x3a5)) {
                    data = data or if (state and AFTER_VOWEL_WITH_PRECOMPOSED_ACCENT != 0) HAS_DIALYTIKA else HAS_COMBINING_DIALYTIKA
                }
                var numYpogegrammeni = if (data and HAS_YPOGEGRAMMENI != 0) 1 else 0
                val hasPrecomposedAccent = data and HAS_ACCENT != 0
                while (nextIndex < src.length) {
                    val diacriticData = getDiacriticData(src[nextIndex].code)
                    if (diacriticData == 0) break
                    data = data or diacriticData
                    if (diacriticData and HAS_YPOGEGRAMMENI != 0) ++numYpogegrammeni
                    ++nextIndex
                }
                if (data and HAS_VOWEL_AND_ACCENT_AND_DIALYTIKA == HAS_VOWEL_AND_ACCENT) {
                    nextState = nextState or if (hasPrecomposedAccent) AFTER_VOWEL_WITH_PRECOMPOSED_ACCENT else AFTER_VOWEL_WITH_COMBINING_ACCENT
                }
                var addTonos = false
                if (upper == 0x397 && data and HAS_ACCENT != 0 && numYpogegrammeni == 0 &&
                    state and AFTER_CASED == 0 && !isFollowedByCasedLetter(src, nextIndex)) {
                    if (hasPrecomposedAccent) upper = 0x389 else addTonos = true
                } else if (data and HAS_DIALYTIKA != 0) {
                    if (upper == 0x399) {
                        upper = 0x3aa
                        data = data and HAS_EITHER_DIALYTIKA.inv()
                    } else if (upper == 0x3a5) {
                        upper = 0x3ab
                        data = data and HAS_EITHER_DIALYTIKA.inv()
                    }
                }
                var change: Boolean
                if (edits == null && options and CaseMapImpl.OMIT_UNCHANGED_TEXT == 0) {
                    change = true
                } else {
                    change = src[index].code != upper || numYpogegrammeni > 0
                    var i2 = index + 1
                    if (data and HAS_EITHER_DIALYTIKA != 0) {
                        change = change or (i2 >= nextIndex || src[i2].code != 0x308)
                        ++i2
                    }
                    if (addTonos) {
                        change = change or (i2 >= nextIndex || src[i2].code != 0x301)
                        ++i2
                    }
                    val oldLength = nextIndex - index
                    val newLength = i2 - index + numYpogegrammeni
                    change = change or (oldLength != newLength)
                    if (change) edits?.addReplace(oldLength, newLength)
                    else {
                        edits?.addUnchanged(oldLength)
                        change = options and CaseMapImpl.OMIT_UNCHANGED_TEXT == 0
                    }
                }
                if (change) {
                    dest.append(upper.toChar())
                    if (data and HAS_EITHER_DIALYTIKA != 0) dest.append('\u0308')
                    if (addTonos) dest.append('\u0301')
                    repeat(numYpogegrammeni) { dest.append('\u0399') }
                }
            } else {
                c = UCaseProps.INSTANCE.toFullUpper(c, null, dest, UCaseProps.LOC_GREEK)
                CaseMapImpl.appendResult(c, dest, nextIndex - index, options, edits)
            }
            index = nextIndex
            state = nextState
        }
    }
}
