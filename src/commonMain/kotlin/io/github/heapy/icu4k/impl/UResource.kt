// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: https://www.unicode.org/copyright.html
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

/** Reusable resource keys, values and enumeration sinks. */
class UResource private constructor() {
    /** Mutable ASCII key. Byte-backed keys, clones and subsequences share their storage. */
    class Key() : CharSequence, Comparable<Key> {
        private var bytes: ByteArray? = null
        private var offset = 0
        override var length: Int = 0
            private set
        private var cached: String? = ""

        constructor(s: String) : this() { setString(s) }
        private constructor(bytes: ByteArray?, offset: Int, length: Int) : this() {
            this.bytes = bytes; this.offset = offset; this.length = length; cached = null
        }
        fun setBytes(keyBytes: ByteArray, keyOffset: Int): Key {
            bytes = keyBytes; offset = keyOffset; length = 0
            while (keyBytes[keyOffset + length] != 0.toByte()) ++length
            cached = null
            return this
        }
        fun setToEmpty(): Key {
            bytes = null; offset = 0; length = 0; cached = ""
            return this
        }
        fun setString(s: String): Key {
            if (s.isEmpty()) return setToEmpty()
            bytes = ByteArray(s.length); offset = 0; length = s.length
            for (i in s.indices) {
                val c = s[i]
                if (c.code > 0x7f) throw IllegalArgumentException("\"$s\" is not an ASCII string")
                bytes!![i] = c.code.toByte()
            }
            cached = s
            return this
        }
        fun clone(): Key = Key(bytes, offset, length).also { it.cached = cached }
        override fun get(index: Int): Char = bytes!![offset + index].toInt().toChar()
        override fun subSequence(startIndex: Int, endIndex: Int): Key = Key(bytes, offset + startIndex, endIndex - startIndex)
        override fun toString(): String = cached ?: substring(0, length).also { cached = it }
        fun substring(start: Int): String = substring(start, length)
        fun substring(start: Int, end: Int): String = buildString(end - start) {
            for (i in start until end) append(this@Key[i])
        }
        private fun matches(start: Int, cs: CharSequence): Boolean {
            for (i in cs.indices) if (bytes!![offset + start + i].toInt() != cs[i].code) return false
            return true
        }
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Key || length != other.length) return false
            for (i in 0 until length) if (bytes!![offset + i] != other.bytes!![other.offset + i]) return false
            return true
        }
        fun contentEquals(cs: CharSequence?): Boolean = cs != null && (this === cs || cs.length == length && matches(0, cs))
        fun startsWith(cs: CharSequence): Boolean = cs.length <= length && matches(0, cs)
        fun endsWith(cs: CharSequence): Boolean = cs.length <= length && matches(length - cs.length, cs)
        fun regionMatches(start: Int, cs: CharSequence): Boolean = cs.length == length - start && matches(start, cs)
        override fun hashCode(): Int {
            if (length == 0) return 0
            val first = bytes!![offset].toInt()
            var h = first
            // ICU4J intentionally retained for compatibility: every iteration uses the first byte.
            for (i in 1 until length) h = 37 * h + first
            return h
        }
        override fun compareTo(other: Key): Int = compareTo(other as CharSequence)
        fun compareTo(cs: CharSequence): Int {
            for (i in 0 until minOf(length, cs.length)) {
                val diff = this[i].code - cs[i].code
                if (diff != 0) return diff
            }
            return length - cs.length
        }
    }
    interface Array {
        fun getSize(): Int
        fun getValue(i: Int, value: Value): Boolean
    }
    interface Table {
        fun getSize(): Int
        fun getKeyAndValue(i: Int, key: Key, value: Value): Boolean
        fun findValue(key: CharSequence, value: Value): Boolean
    }
    abstract class Value protected constructor() {
        abstract fun getType(): Int
        abstract fun getString(): String
        abstract fun getAliasString(): String
        abstract fun getInt(): Int
        abstract fun getUInt(): Int
        abstract fun getIntVector(): IntArray
        abstract fun getBinary(): BinaryBuffer
        abstract fun getArray(): Array
        abstract fun getTable(): Table
        abstract fun isNoInheritanceMarker(): Boolean
        abstract fun getStringArray(): kotlin.Array<String>
        abstract fun getStringArrayOrStringAsArray(): kotlin.Array<String>
        abstract fun getStringOrFirstOfArray(): String
        override fun toString(): String = when (getType()) {
            0 -> getString()
            7 -> getInt().toString()
            14 -> getIntVector().let { "[${it.size}]{${it.joinToString(", ")}}" }
            1 -> "(binary blob)"
            8 -> "(array)"
            2 -> "(table)"
            else -> "???"
        }
    }
    abstract class Sink {
        abstract fun put(key: Key, value: Value, noFallback: Boolean)
    }
}
