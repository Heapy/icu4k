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
    fun getUnicodeLocaleAttributes(): Set<String> = LocaleReadOnlySet({ attributes }, attributeOrder)
    fun getUnicodeLocaleKeys(): Set<String> = LocaleReadOnlySet({ keywords.keys }, keywordOrder)
    fun getUnicodeLocaleType(key: String): String? = if (keywordOrder == null) keywords[key] else
        keywords.entries.firstOrNull { keywordOrder.compare(it.key, key) == 0 }?.value

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
