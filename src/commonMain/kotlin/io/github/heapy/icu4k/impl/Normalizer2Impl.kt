// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Normalization implementation being ported; currently Hangul and UTF-16 primitives. */
internal class Normalizer2Impl {
    private var minDecompNoCP = 0
    private var minCompNoMaybeCP = 0
    private var minLcccCP = 0
    private var minYesNo = 0
    private var minYesNoMappingsOnly = 0
    private var minNoNo = 0
    private var minNoNoCompBoundaryBefore = 0
    private var minNoNoCompNoMaybeCC = 0
    private var minNoNoEmpty = 0
    private var limitNoNo = 0
    private var minMaybeNo = 0
    private var minMaybeNoCombinesFwd = 0
    private var minMaybeYes = 0
    private var centerNoNoDelta = 0
    private lateinit var dataVersion: io.github.heapy.icu4k.util.VersionInfo
    private lateinit var normTrie: io.github.heapy.icu4k.util.CodePointTrie
    private var extraData: String? = null
    private lateinit var smallFCD: ByteArray
    fun load(name: String): Normalizer2Impl = load(ICUBinary.getRequiredData(name))
    fun load(bytes: io.github.heapy.icu4k.util.BinaryBuffer): Normalizer2Impl {
        dataVersion = ICUBinary.readHeaderAndDataVersion(bytes, 0x4e726d32, ICUBinary.Authenticate { it[0].toInt() == 5 })
        val count = bytes.readInt() / 4
        if (count <= 18) throw io.github.heapy.icu4k.util.ICUUncheckedIOException("Normalizer2 data: not enough indexes")
        val indexes = IntArray(count)
        indexes[0] = count * 4
        for (i in 1 until count) indexes[i] = bytes.readInt()
        minDecompNoCP = indexes[8]
        minCompNoMaybeCP = indexes[9]
        minLcccCP = indexes[18]
        minYesNo = indexes[10]
        minYesNoMappingsOnly = indexes[14]
        minNoNo = indexes[11]
        minNoNoCompBoundaryBefore = indexes[15]
        minNoNoCompNoMaybeCC = indexes[16]
        minNoNoEmpty = indexes[17]
        limitNoNo = indexes[12]
        minMaybeNo = indexes[20]
        minMaybeNoCombinesFwd = indexes[21]
        minMaybeYes = indexes[13]
        centerNoNoDelta = (minMaybeNo shr 3) - 0x40 - 1
        val triePosition = bytes.position
        normTrie = io.github.heapy.icu4k.util.CodePointTrie.fromBinary(io.github.heapy.icu4k.util.CodePointTrie.Type.FAST, io.github.heapy.icu4k.util.CodePointTrie.ValueWidth.BITS_16, bytes)
        val trieLength = bytes.position - triePosition
        if (trieLength > indexes[1] - indexes[0]) throw io.github.heapy.icu4k.util.ICUUncheckedIOException("Normalizer2 data: not enough bytes for normTrie")
        ICUBinary.skipBytes(bytes, indexes[1] - indexes[0] - trieLength)
        val numChars = (indexes[2] - indexes[1]) / 2
        if (numChars != 0) extraData = ICUBinary.getString(bytes, numChars, 0)
        smallFCD = bytes.readBytes(256)
        return this
    }
    fun getNorm16(c: Int): Int = if (UTF16Plus.isLeadSurrogate(c)) 1 else normTrie[c]
    fun getRawNorm16(c: Int): Int = normTrie[c]

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
