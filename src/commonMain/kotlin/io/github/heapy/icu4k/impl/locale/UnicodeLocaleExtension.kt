// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

/** Unicode attributes/keywords; adopts nonempty backing collections as ICU does. */
class UnicodeLocaleExtension internal constructor(
    attributes: Set<String>?, keywords: Map<String, String>?,
    private val attributeOrder: Comparator<String>? = null,
    private val keywordOrder: Comparator<String>? = null,
) : Extension(SINGLETON) {
    private val attributes = attributes?.takeIf { it.isNotEmpty() } ?: emptySet()
    private val keywords = keywords?.takeIf { it.isNotEmpty() } ?: emptyMap()
    init {
        if (this.attributes.isNotEmpty() || this.keywords.isNotEmpty()) {
            val parts = ArrayList<String>()
            parts.addAll(this.attributes.sortedWith(attributeOrder ?: naturalOrder()))
            for (key in this.keywords.keys.sortedWith(keywordOrder ?: naturalOrder())) {
                parts.add(key)
                val value = this.keywords.getValue(key)
                if (value.isNotEmpty()) parts.add(value)
            }
            _value = parts.joinToString(LanguageTag.SEP)
        }
    }
    fun getUnicodeLocaleAttributes(): Set<String> = ReadOnlySortedSet({ attributes }, attributeOrder)
    fun getUnicodeLocaleKeys(): Set<String> = ReadOnlySortedSet({ keywords.keys }, keywordOrder)
    fun getUnicodeLocaleType(key: String): String? = if (keywordOrder == null) keywords[key] else
        keywords.entries.firstOrNull { keywordOrder.compare(it.key, key) == 0 }?.value

    /** A live view which also rejects mutation through MutableSet casts. */
    private class ReadOnlySortedSet(private val values: () -> Set<String>, private val order: Comparator<String>?) : AbstractMutableSet<String>() {
        override val size: Int get() = values().size
        override fun contains(element: String): Boolean = if (order == null) values().contains(element) else
            values().any { order.compare(it, element) == 0 }
        override fun iterator(): MutableIterator<String> {
            val source = values().sortedWith(order ?: naturalOrder()).iterator()
            return object : MutableIterator<String> {
                override fun hasNext(): Boolean = source.hasNext()
                override fun next(): String = source.next()
                override fun remove(): Nothing = throw UnsupportedOperationException()
            }
        }
        override fun add(element: String): Nothing = throw UnsupportedOperationException()
        override fun addAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
        override fun remove(element: String): Nothing = throw UnsupportedOperationException()
        override fun removeAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
        override fun retainAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
        override fun clear(): Nothing = throw UnsupportedOperationException()
    }
    companion object {
        const val SINGLETON = 'u'
        val CA_JAPANESE = UnicodeLocaleExtension(null, mapOf("ca" to "japanese"))
        val NU_THAI = UnicodeLocaleExtension(null, mapOf("nu" to "thai"))
        fun isSingletonChar(c: Char): Boolean = SINGLETON == AsciiUtil.toLower(c)
        fun isAttribute(s: String): Boolean = s.length in 3..8 && AsciiUtil.isAlphaNumericString(s)
        fun isKey(s: String): Boolean = s.length == 2 && AsciiUtil.isAlphaNumeric(s[0]) && AsciiUtil.isAlpha(s[1])
        fun isTypeSubtag(s: String): Boolean = s.length in 3..8 && AsciiUtil.isAlphaNumericString(s)
        fun isType(s: String): Boolean {
            var start = 0
            while (true) {
                val end = s.indexOf(LanguageTag.SEP, start)
                val subtag = if (end < 0) s.substring(start) else s.substring(start, end)
                if (!isTypeSubtag(subtag)) return false
                if (end < 0) break
                start = end + 1
            }
            return start < s.length
        }
    }
}
