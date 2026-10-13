// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Normalization implementation being ported; currently Hangul and UTF-16 primitives. */
internal class Normalizer2Impl {
    object Hangul {
        const val JAMO_L_BASE = 0x1100
        const val JAMO_L_END = 0x1112
        const val JAMO_V_BASE = 0x1161
        const val JAMO_V_END = 0x1175
        const val JAMO_T_BASE = 0x11a7
        const val JAMO_T_END = 0x11c2
        const val HANGUL_BASE = 0xac00
        const val HANGUL_END = 0xd7a3
        const val JAMO_L_COUNT = 19
        const val JAMO_V_COUNT = 21
        const val JAMO_T_COUNT = 28
        const val JAMO_L_LIMIT = JAMO_L_BASE + JAMO_L_COUNT
        const val JAMO_V_LIMIT = JAMO_V_BASE + JAMO_V_COUNT
        const val JAMO_VT_COUNT = JAMO_V_COUNT * JAMO_T_COUNT
        const val HANGUL_COUNT = JAMO_L_COUNT * JAMO_V_COUNT * JAMO_T_COUNT
        const val HANGUL_LIMIT = HANGUL_BASE + HANGUL_COUNT
        fun isHangul(c: Int): Boolean = c >= HANGUL_BASE && c < HANGUL_LIMIT
        fun isHangulLV(c: Int): Boolean {
            val offset = c - HANGUL_BASE
            return offset >= 0 && offset < HANGUL_COUNT && offset % JAMO_T_COUNT == 0
        }
        fun isJamoL(c: Int): Boolean = c >= JAMO_L_BASE && c < JAMO_L_LIMIT
        fun isJamoV(c: Int): Boolean = c >= JAMO_V_BASE && c < JAMO_V_LIMIT
        fun isJamoT(c: Int): Boolean { val t = c - JAMO_T_BASE; return t > 0 && t < JAMO_T_COUNT }
        fun isJamo(c: Int): Boolean = c >= JAMO_L_BASE && c <= JAMO_T_END && (c <= JAMO_L_END || c in JAMO_V_BASE..JAMO_V_END || c > JAMO_T_BASE)
        /** Input must be a Hangul syllable. Appends its recursive decomposition. */
        fun decompose(c: Int, buffer: Appendable): Int {
            var offset = c - HANGUL_BASE
            val trail = offset % JAMO_T_COUNT
            offset /= JAMO_T_COUNT
            buffer.append((JAMO_L_BASE + offset / JAMO_V_COUNT).toChar())
            buffer.append((JAMO_V_BASE + offset % JAMO_V_COUNT).toChar())
            if (trail == 0) return 2
            buffer.append((JAMO_T_BASE + trail).toChar())
            return 3
        }
        /** Input must be a Hangul syllable. The raw decomposition always has two units. */
        fun getRawDecomposition(c: Int, buffer: Appendable) {
            var offset = c - HANGUL_BASE
            val trail = offset % JAMO_T_COUNT
            if (trail == 0) {
                offset /= JAMO_T_COUNT
                buffer.append((JAMO_L_BASE + offset / JAMO_V_COUNT).toChar())
                buffer.append((JAMO_V_BASE + offset % JAMO_V_COUNT).toChar())
            } else {
                buffer.append((c - trail).toChar())
                buffer.append((JAMO_T_BASE + trail).toChar())
            }
        }
    }
    object UTF16Plus {
        fun isLeadSurrogate(c: Int): Boolean = c and -1024 == 0xd800
        fun isTrailSurrogate(c: Int): Boolean = c and -1024 == 0xdc00
        fun isSurrogate(c: Int): Boolean = c and -2048 == 0xd800
        fun isSurrogateLead(c: Int): Boolean = c and 0x400 == 0
        fun equal(s1: CharSequence, s2: CharSequence): Boolean {
            if (s1 === s2) return true
            if (s1.length != s2.length) return false
            for (i in s1.indices) if (s1[i] != s2[i]) return false
            return true
        }
        fun equal(s1: CharSequence, start1: Int, limit1: Int, s2: CharSequence, start2: Int, limit2: Int): Boolean {
            if (limit1 - start1 != limit2 - start2) return false
            if (s1 === s2 && start1 == start2) return true
            var i = start1; var j = start2
            while (i < limit1) if (s1[i++] != s2[j++]) return false
            return true
        }
    }
}
