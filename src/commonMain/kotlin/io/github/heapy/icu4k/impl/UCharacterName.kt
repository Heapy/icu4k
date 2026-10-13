// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Character-name data implementation being ported. */
internal class UCharacterName(bytes: io.github.heapy.icu4k.util.BinaryBuffer = ICUBinary.getRequiredData("unames.icu")) {
    internal var tokenTable = CharArray(0)
        private set
    internal var tokenString = ByteArray(0)
        private set
    internal var groupInfo = CharArray(0)
        private set
    internal var groupString = ByteArray(0)
        private set
    internal var algorithms = emptyArray<AlgorithmName>()
        private set
    var m_groupcount_ = 0
        private set
    internal var m_groupsize_ = 0
        private set
    init { UCharacterNameReader(bytes).read(this) }
    internal fun setToken(token: CharArray?, strings: ByteArray?): Boolean {
        if (token == null || token.isEmpty() || strings == null || strings.isEmpty()) return false
        tokenTable = token
        tokenString = strings
        return true
    }
    internal fun setAlgorithm(alg: Array<AlgorithmName>?): Boolean {
        if (alg.isNullOrEmpty()) return false
        algorithms = alg
        return true
    }
    internal fun setGroupCountSize(count: Int, size: Int): Boolean {
        if (count <= 0 || size <= 0) return false
        m_groupcount_ = count
        m_groupsize_ = size
        return true
    }
    internal fun setGroup(group: CharArray?, strings: ByteArray?): Boolean {
        if (group == null || group.isEmpty() || strings == null || strings.isEmpty()) return false
        groupInfo = group
        groupString = strings
        return true
    }
    fun getGroup(codepoint: Int): Int {
        var end = m_groupcount_
        val msb = getCodepointMSB(codepoint)
        var result = 0
        while (result < end - 1) {
            val index = (result + end) shr 1
            if (msb < getGroupMSB(index)) end = index else result = index
        }
        return result
    }
    fun getGroupMSB(index: Int): Int {
        if (index >= m_groupcount_) return -1
        if (index < 0) throw IndexOutOfBoundsException()
        return groupInfo[index * m_groupsize_].code
    }
    fun getGroupLengths(group: Int, offsets: CharArray, lengths: CharArray): Int {
        var length = 0xffff
        val index = group * m_groupsize_
        var stringOffset = UCharacterUtility.toInt(groupInfo[index + 1], groupInfo[index + 2])
        offsets[0] = 0.toChar()
        var i = 0
        while (i < LINES_PER_GROUP_) {
            val b = groupString[stringOffset].toInt()
            var shift = 4
            while (shift >= 0) {
                val n = (b shr shift) and 15
                if (length == 0xffff && n > 11) length = (n - 12) shl 4
                else {
                    lengths[i] = (if (length != 0xffff) (length or n) + 12 else n).toChar()
                    if (i < LINES_PER_GROUP_) offsets[i + 1] = (offsets[i].code + lengths[i].code).toChar()
                    length = 0xffff
                    ++i
                }
                shift -= 4
            }
            ++stringOffset
        }
        return stringOffset
    }
    fun getGroupName(initialIndex: Int, initialLength: Int, choice: Int): String? {
        var index = initialIndex
        var length = initialLength
        if (choice != UCharacterNameChoice.UNICODE_CHAR_NAME && choice != UCharacterNameChoice.EXTENDED_CHAR_NAME) {
            if (';'.code >= tokenTable.size || tokenTable[';'.code].code == 0xffff) {
                var field = if (choice == UCharacterNameChoice.ISO_COMMENT_) 2 else choice
                do {
                    val old = index
                    index += UCharacterUtility.skipByteSubString(groupString, index, length, ';'.code.toByte())
                    length -= index - old
                } while (--field > 0)
            } else length = 0
        }
        val out = StringBuilder()
        var i = 0
        while (i < length) {
            val b = groupString[index + i++].toInt()
            if (b >= tokenTable.size) {
                if (b == ';'.code) break
                out.append(b)
            } else {
                var token = tokenTable[b and 255].code
                if (token == 0xfffe) {
                    token = tokenTable[(b shl 8) or (groupString[index + i].toInt() and 255)].code
                    ++i
                }
                if (token == 0xffff) {
                    if (b == ';'.code) {
                        if (out.isEmpty() && choice == UCharacterNameChoice.EXTENDED_CHAR_NAME) continue
                        break
                    }
                    out.append((b and 255).toChar())
                } else UCharacterUtility.getNullTermByteSubString(out, tokenString, token)
            }
        }
        return out.toString().takeIf { it.isNotEmpty() }
    }
    fun getGroupName(ch: Int, choice: Int): String? {
        val msb = getCodepointMSB(ch)
        val group = getGroup(ch)
        if (msb != groupInfo[group * m_groupsize_].code) return null
        val offsets = CharArray(LINES_PER_GROUP_ + 1)
        val lengths = CharArray(LINES_PER_GROUP_ + 1)
        val index = getGroupLengths(group, offsets, lengths)
        val offset = ch and GROUP_MASK_
        return getGroupName(index + offsets[offset].code, lengths[offset].code, choice)
    }
    fun getAlgorithmLength(): Int = algorithms.size
    fun getAlgorithmStart(index: Int): Int = algorithms[index].rangeStart
    fun getAlgorithmEnd(index: Int): Int = algorithms[index].rangeEnd
    fun getAlgorithmName(index: Int, codepoint: Int): String = StringBuilder().also { algorithms[index].appendName(codepoint, it) }.toString()

    internal class AlgorithmName {
        internal var rangeStart = 0
            private set
        internal var rangeEnd = 0
            private set
        private var type: Byte = 0
        private var variant: Byte = 0
        private var factor: CharArray? = null
        private var prefix: String? = null
        private var factorString: ByteArray? = null
        fun setInfo(start: Int, end: Int, type: Byte, variant: Byte): Boolean {
            if (start >= 0 && start <= end && end <= 0x10ffff && (type == TYPE_0_.toByte() || type == TYPE_1_.toByte())) {
                rangeStart = start; rangeEnd = end; this.type = type; this.variant = variant
                return true
            }
            return false
        }
        fun setFactor(factor: CharArray): Boolean {
            if (factor.size != variant.toInt()) return false
            this.factor = factor
            return true
        }
        fun setPrefix(prefix: String?): Boolean {
            if (prefix.isNullOrEmpty()) return false
            this.prefix = prefix
            return true
        }
        fun setFactorString(bytes: ByteArray?): Boolean { factorString = bytes; return true }
        fun contains(ch: Int): Boolean = ch in rangeStart..rangeEnd
        private fun indexesFor(ch: Int): IntArray {
            val indexes = IntArray(256)
            var offset = ch - rangeStart
            for (i in variant.toInt() - 1 downTo 1) {
                val f = factor!![i].code and 255
                indexes[i] = offset % f
                offset /= f
            }
            indexes[0] = offset
            return indexes
        }
        fun appendName(ch: Int, str: StringBuilder) {
            str.append(prefix)
            when (type.toInt()) {
                TYPE_0_ -> str.append(Utility.hex(ch.toLong(), variant.toInt()))
                TYPE_1_ -> str.append(getFactorString(indexesFor(ch), variant.toInt()))
            }
        }
        fun getChar(name: String): Int {
            val length = prefix!!.length
            if (name.length < length || name.substring(0, length) != prefix) return -1
            when (type.toInt()) {
                TYPE_0_ -> {
                    val result = name.substring(length).toIntOrNull(16) ?: return -1
                    if (result in rangeStart..rangeEnd) return result
                }
                TYPE_1_ -> for (ch in rangeStart..rangeEnd) {
                    if (compareFactorString(indexesFor(ch), variant.toInt(), name, length)) return ch
                }
            }
            return -1
        }
        fun add(set: IntArray, maxlength: Int): Int {
            var length = addChars(set, prefix!!)
            when (type.toInt()) {
                TYPE_0_ -> length += variant.toInt()
                TYPE_1_ -> for (i in variant.toInt() - 1 downTo 1) {
                    var maxFactorLength = 0
                    var count = 0
                    for (f in factor!![i].code downTo 1) {
                        val buffer = StringBuilder()
                        count = UCharacterUtility.getNullTermByteSubString(buffer, factorString!!, count)
                        addChars(set, buffer)
                        if (buffer.length > maxFactorLength) maxFactorLength = buffer.length
                    }
                    length += maxFactorLength
                }
            }
            return maxOf(length, maxlength)
        }
        private fun getFactorString(indexes: IntArray?, length: Int): String? {
            val factors = factor!!
            if (indexes == null || length != factors.size) return null
            val out = StringBuilder()
            var count = 0
            for (i in factors.indices) {
                count = UCharacterUtility.skipNullTermByteSubString(factorString!!, count, indexes[i])
                count = UCharacterUtility.getNullTermByteSubString(out, factorString!!, count)
                if (i != factors.lastIndex) count = UCharacterUtility.skipNullTermByteSubString(factorString!!, count, factors[i].code - indexes[i] - 1)
            }
            return out.toString()
        }
        private fun compareFactorString(indexes: IntArray?, length: Int, str: String, offset: Int): Boolean {
            val factors = factor!!
            if (indexes == null || length != factors.size) return false
            var count = 0
            var stringCount = offset
            for (i in factors.indices) {
                count = UCharacterUtility.skipNullTermByteSubString(factorString!!, count, indexes[i])
                stringCount = UCharacterUtility.compareNullTermByteSubString(str, factorString!!, stringCount, count)
                if (stringCount < 0) return false
                if (i != factors.lastIndex) count = UCharacterUtility.skipNullTermByteSubString(factorString!!, count, factors[i].code - indexes[i])
            }
            return stringCount == str.length
        }
        companion object {
            const val TYPE_0_ = 0
            const val TYPE_1_ = 1
        }
    }
    companion object {
        const val LINES_PER_GROUP_ = 32
        private const val GROUP_SHIFT_ = 5
        private const val GROUP_MASK_ = LINES_PER_GROUP_ - 1
        fun getCodepointMSB(codepoint: Int): Int = codepoint shr GROUP_SHIFT_
        fun getGroupLimit(msb: Int): Int = (msb shl GROUP_SHIFT_) + LINES_PER_GROUP_
        fun getGroupMin(msb: Int): Int = msb shl GROUP_SHIFT_
        fun getGroupOffset(codepoint: Int): Int = codepoint and GROUP_MASK_
        fun getGroupMinFromCodepoint(codepoint: Int): Int = codepoint and GROUP_MASK_.inv()
        val INSTANCE: UCharacterName by lazy { UCharacterName() }
        private fun addChars(set: IntArray, str: CharSequence): Int {
            for (i in str.length - 1 downTo 0) {
                val ch = str[i].code
                val index = ch ushr 5
                set[index] = set[index] or (1 shl (ch and 31))
            }
            return str.length
        }
    }
}
