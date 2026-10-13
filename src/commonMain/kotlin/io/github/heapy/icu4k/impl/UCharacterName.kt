// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Character-name data implementation being ported. */
internal class UCharacterName {
    internal class AlgorithmName {
        private var rangeStart = 0
        private var rangeEnd = 0
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
