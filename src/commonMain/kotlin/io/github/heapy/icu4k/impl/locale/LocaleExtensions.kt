// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.impl.locale.InternalLocaleBuilder.CaseInsensitiveChar
import io.github.heapy.icu4k.impl.locale.InternalLocaleBuilder.CaseInsensitiveString

class LocaleExtensions private constructor(private val map: Map<Char, Extension>, private val id: String) {
    internal constructor(extensions: Map<CaseInsensitiveChar, String>?, attributes: Set<CaseInsensitiveString>?, keywords: Map<CaseInsensitiveString, String>?) :
        this(buildMap(extensions, attributes, keywords))
    private constructor(map: Map<Char, Extension>) : this(map, toID(map))
    fun getKeys(): Set<Char> = LocaleReadOnlySet({ map.keys }, null)
    fun getExtension(key: Char): Extension? = map[AsciiUtil.toLower(key)]
    fun getExtensionValue(key: Char): String? = getExtension(key)?.getValue()
    fun getUnicodeLocaleAttributes(): Set<String> = (map[UnicodeLocaleExtension.SINGLETON] as UnicodeLocaleExtension?)?.getUnicodeLocaleAttributes() ?: emptySet()
    fun getUnicodeLocaleKeys(): Set<String> = (map[UnicodeLocaleExtension.SINGLETON] as UnicodeLocaleExtension?)?.getUnicodeLocaleKeys() ?: emptySet()
    fun getUnicodeLocaleType(key: String): String? = (map[UnicodeLocaleExtension.SINGLETON] as UnicodeLocaleExtension?)?.getUnicodeLocaleType(AsciiUtil.toLowerString(key))
    fun isEmpty(): Boolean = map.isEmpty()
    fun getID(): String = id
    override fun toString(): String = id
    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = this === other || other is LocaleExtensions && id == other.id
    companion object {
        val EMPTY_EXTENSIONS = LocaleExtensions(emptyMap(), "")
        val CALENDAR_JAPANESE = LocaleExtensions(mapOf('u' to UnicodeLocaleExtension.CA_JAPANESE), "u-ca-japanese")
        val NUMBER_THAI = LocaleExtensions(mapOf('u' to UnicodeLocaleExtension.NU_THAI), "u-nu-thai")
        fun isValidKey(c: Char): Boolean = LanguageTag.isExtensionSingletonChar(c) || LanguageTag.isPrivateusePrefixChar(c)
        fun isValidUnicodeLocaleKey(key: String): Boolean = UnicodeLocaleExtension.isKey(key)
        private fun buildMap(extensions: Map<CaseInsensitiveChar, String>?, attributes: Set<CaseInsensitiveString>?, keywords: Map<CaseInsensitiveString, String>?): Map<Char, Extension> {
            val result = HashMap<Char, Extension>()
            if (extensions != null) for ((entry, original) in extensions) {
                val key = AsciiUtil.toLower(entry.value())
                val value = if (LanguageTag.isPrivateusePrefixChar(key)) InternalLocaleBuilder.removePrivateuseVariant(original) ?: continue else original
                result[key] = Extension(key, AsciiUtil.toLowerString(value))
            }
            if (!attributes.isNullOrEmpty() || !keywords.isNullOrEmpty()) {
                val attrs = attributes?.map { AsciiUtil.toLowerString(it.value()) }?.toSet()
                val types = keywords?.entries?.associate { AsciiUtil.toLowerString(it.key.value()) to AsciiUtil.toLowerString(it.value) }
                result[UnicodeLocaleExtension.SINGLETON] = UnicodeLocaleExtension(attrs, types)
            }
            return result
        }
        private fun toID(map: Map<Char, Extension>): String {
            val normal = map.keys.sorted().filterNot(LanguageTag::isPrivateusePrefixChar).map { map.getValue(it).getID() }.toMutableList()
            map[LanguageTag.PRIVATEUSE[0]]?.let { normal.add(it.getID()) }
            return normal.joinToString(LanguageTag.SEP)
        }
    }
}
