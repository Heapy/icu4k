// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

/** ICU base locale with ASCII normalization and GC-aware canonical instance caching. */
class BaseLocale private constructor(language: String, script: String, region: String, variant: String) {
    private val language = internLocaleString(AsciiUtil.toLowerString(language))
    private val script = internLocaleString(AsciiUtil.toTitleString(script))
    private val region = internLocaleString(AsciiUtil.toUpperString(region))
    private val variant = internLocaleString(AsciiUtil.toUpperString(variant))
    private val hash = hashFields(this.language, this.script, this.region, this.variant, false)
    fun getLanguage(): String = language
    fun getScript(): String = script
    fun getRegion(): String = region
    fun getVariant(): String = variant
    override fun hashCode(): Int = hash
    override fun equals(other: Any?): Boolean = this === other || other is BaseLocale && hash == other.hash &&
        language == other.language && script == other.script && region == other.region && variant == other.variant
    override fun toString(): String = buildString {
        fun field(name: String, value: String) {
            if (value.isNotEmpty()) { if (isNotEmpty()) append(", "); append(name).append('=').append(value) }
        }
        field("language", language); field("script", script); field("region", region); field("variant", variant)
    }
    internal class Key(language: String?, script: String?, region: String?, variant: String?) : Comparable<Key> {
        val language = language ?: ""
        val script = script ?: ""
        val region = region ?: ""
        val variant = variant ?: ""
        private val hash = hashFields(this.language, this.script, this.region, this.variant, true)
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = this === other || other is Key &&
            AsciiUtil.caseIgnoreMatch(language, other.language) && AsciiUtil.caseIgnoreMatch(script, other.script) &&
            AsciiUtil.caseIgnoreMatch(region, other.region) && AsciiUtil.caseIgnoreMatch(variant, other.variant)
        override fun compareTo(other: Key): Int {
            var result = AsciiUtil.caseIgnoreCompare(language, other.language)
            if (result == 0) result = AsciiUtil.caseIgnoreCompare(script, other.script)
            if (result == 0) result = AsciiUtil.caseIgnoreCompare(region, other.region)
            if (result == 0) result = AsciiUtil.caseIgnoreCompare(variant, other.variant)
            return result
        }
        companion object {
            fun normalize(key: Key): Key = Key(internLocaleString(AsciiUtil.toLowerString(key.language)),
                internLocaleString(AsciiUtil.toTitleString(key.script)), internLocaleString(AsciiUtil.toUpperString(key.region)),
                internLocaleString(AsciiUtil.toUpperString(key.variant)))
        }
    }
    private class Cache : LocaleObjectCache<Key, BaseLocale>() {
        override fun normalizeKey(key: Key): Key = Key.normalize(key)
        override fun createObject(key: Key): BaseLocale = BaseLocale(key.language, key.script, key.region, key.variant)
    }
    companion object {
        const val SEP = "_"
        private val CACHE = Cache()
        val ROOT = getInstance("", "", "", "")
        fun getInstance(language: String?, script: String?, region: String?, variant: String?): BaseLocale =
            CACHE.get(Key(language, script, region, variant))!!
        private fun hashFields(language: String, script: String, region: String, variant: String, lower: Boolean): Int {
            var hash = 0
            for (field in arrayOf(language, script, region, variant)) for (c in field) hash = 31 * hash + (if (lower) AsciiUtil.toLower(c) else c).code
            return hash
        }
    }
}

internal expect fun internLocaleString(value: String): String
