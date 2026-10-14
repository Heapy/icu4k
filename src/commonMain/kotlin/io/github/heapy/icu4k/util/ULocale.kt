// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.LocaleIDParser
import io.github.heapy.icu4k.impl.LocaleIDs
import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.impl.locale.*

/** ICU locale identity and BCP47 conversion. Display/default/resource APIs are still being ported. */
class ULocale private constructor(localeID: String, normalize: Boolean) : Comparable<ULocale> {
    private val localeID = if (normalize) getName(localeID) else localeID
    constructor(localeID: String) : this(localeID, true)
    constructor(a: String?, b: String?) : this(a, b, null)
    constructor(a: String?, b: String?, c: String?) : this(lscvToID(a, b, c, ""))
    private val base: BaseLocale by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (this.localeID.isEmpty()) BaseLocale.ROOT else LocaleIDParser(this.localeID).let {
            BaseLocale.getInstance(it.getLanguage(), it.getScript(), it.getCountry(), it.getVariant())
        }
    }
    private val extensions: LocaleExtensions by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val keywords = getKeywords()
        if (keywords == null) LocaleExtensions.EMPTY_EXTENSIONS else {
            val builder = InternalLocaleBuilder()
            while (keywords.hasNext()) {
                val key = keywords.next()
                val value = getKeywordValue(key)!!
                if (key == "attribute") {
                    for (attribute in value.split('-', '_')) try { builder.addUnicodeLocaleAttribute(attribute) } catch (_: LocaleSyntaxException) { }
                } else if (key.length >= 2) {
                    val bcpKey = toUnicodeLocaleKey(key); val bcpType = toUnicodeLocaleType(key, value)
                    if (bcpKey != null && bcpType != null) try { builder.setUnicodeLocaleKeyword(bcpKey, bcpType) } catch (_: LocaleSyntaxException) { }
                } else if (key.length == 1 && key[0] != UNICODE_LOCALE_EXTENSION) {
                    try { builder.setExtension(key[0], value.replace('_', '-')) } catch (_: LocaleSyntaxException) { }
                }
            }
            builder.getLocaleExtensions()
        }
    }
    fun clone(): ULocale = this
    fun getName(): String = localeID
    fun getBaseName(): String = getBaseName(localeID)
    fun getLanguage(): String = base.getLanguage()
    fun getScript(): String = base.getScript()
    fun getCountry(): String = base.getRegion()
    fun getVariant(): String = base.getVariant()
    fun getKeywords(): MutableIterator<String>? = getKeywords(localeID)
    fun getKeywordValue(key: String?): String? = getKeywordValue(localeID, key)
    fun setKeywordValue(key: String?, value: String?): ULocale = ULocale(setKeywordValue(localeID, key, value), false)
    fun getFallback(): ULocale? = if (localeID.isEmpty() || localeID[0] == '@') null else ULocale(fallbackString(localeID), false)
    fun getISO3Language(): String = getISO3Language(localeID)
    fun getISO3Country(): String = getISO3Country(localeID)
    fun getExtension(key: Char): String? {
        if (!LocaleExtensions.isValidKey(key)) throw IllegalArgumentException("Invalid extension key: $key")
        return extensions.getExtensionValue(key)
    }
    fun getExtensionKeys(): Set<Char> = extensions.getKeys()
    fun getUnicodeLocaleAttributes(): Set<String> = extensions.getUnicodeLocaleAttributes()
    fun getUnicodeLocaleKeys(): Set<String> = extensions.getUnicodeLocaleKeys()
    fun getUnicodeLocaleType(key: String): String? {
        if (!LocaleExtensions.isValidUnicodeLocaleKey(key)) throw IllegalArgumentException("Invalid Unicode locale key: $key")
        return extensions.getUnicodeLocaleType(key)
    }
    override fun toString(): String = localeID
    override fun hashCode(): Int = localeID.hashCode()
    override fun equals(other: Any?): Boolean = this === other || other is ULocale && localeID == other.localeID
    override fun compareTo(other: ULocale): Int {
        if (this === other) return 0
        var cmp = getLanguage().compareTo(other.getLanguage())
        if (cmp == 0) cmp = getScript().compareTo(other.getScript())
        if (cmp == 0) cmp = getCountry().compareTo(other.getCountry())
        if (cmp == 0) cmp = getVariant().compareTo(other.getVariant())
        if (cmp != 0) return cmp.compareTo(0)
        val a = getKeywords(); val b = other.getKeywords()
        if (a == null) return if (b == null) 0 else -1
        if (b == null) return 1
        while (a.hasNext()) {
            if (!b.hasNext()) return 1
            val ak = a.next(); val bk = b.next()
            val keys = ak.compareTo(bk); if (keys != 0) return keys.compareTo(0)
            val av = getKeywordValue(ak); val bv = other.getKeywordValue(bk)
            val values = if (av == null) { if (bv == null) 0 else -1 } else if (bv == null) 1 else av.compareTo(bv)
            if (values != 0) return values.compareTo(0)
        }
        return if (b.hasNext()) -1 else 0
    }
    fun toLanguageTag(): String {
        var b = base; var e = extensions
        if (b.getVariant().equals("POSIX", ignoreCase = true)) {
            b = BaseLocale.getInstance(b.getLanguage(), b.getScript(), b.getRegion(), "")
            if (e.getUnicodeLocaleType("va") == null) {
                val builder = InternalLocaleBuilder()
                try { builder.setLocale(BaseLocale.ROOT, e); builder.setUnicodeLocaleKeyword("va", "posix"); e = builder.getLocaleExtensions() }
                catch (error: LocaleSyntaxException) { throw RuntimeException(error) }
            }
        }
        val tag = LanguageTag.parseLocale(b, e)
        return buildString {
            if (tag.getLanguage().isNotEmpty()) append(LanguageTag.canonicalizeLanguage(tag.getLanguage()))
            if (tag.getScript().isNotEmpty()) append('-').append(LanguageTag.canonicalizeScript(tag.getScript()))
            if (tag.getRegion().isNotEmpty()) append('-').append(LanguageTag.canonicalizeRegion(tag.getRegion()))
            for (variant in tag.getVariants().sorted()) append('-').append(LanguageTag.canonicalizeVariant(variant))
            for (extension in tag.getExtensions()) append('-').append(LanguageTag.canonicalizeExtension(extension))
            if (tag.getPrivateuse().isNotEmpty()) {
                if (isEmpty()) append("und")
                append("-x-").append(LanguageTag.canonicalizePrivateuse(tag.getPrivateuse()))
            }
        }
    }
    class Builder {
        private val builder = InternalLocaleBuilder()
        private fun mutate(block: () -> Unit): Builder {
            try { block() } catch (error: LocaleSyntaxException) { throw IllformedLocaleException(error.message, error.getErrorIndex()) }
            return this
        }
        fun setLocale(locale: ULocale): Builder = mutate { builder.setLocale(locale.base, locale.extensions) }
        fun setLanguageTag(tag: String): Builder {
            val status = ParseStatus(); val parsed = LanguageTag.parse(tag, status)
            if (status.isError()) throw IllformedLocaleException(status.getErrorMessage(), status.getErrorIndex())
            builder.setLanguageTag(parsed); return this
        }
        fun setLanguage(value: String?): Builder = mutate { builder.setLanguage(value) }
        fun setScript(value: String?): Builder = mutate { builder.setScript(value) }
        fun setRegion(value: String?): Builder = mutate { builder.setRegion(value) }
        fun setVariant(value: String?): Builder = mutate { builder.setVariant(value) }
        fun setExtension(key: Char, value: String?): Builder = mutate { builder.setExtension(key, value) }
        fun setUnicodeLocaleKeyword(key: String, value: String?): Builder = mutate { builder.setUnicodeLocaleKeyword(key, value) }
        fun addUnicodeLocaleAttribute(value: String?): Builder = mutate { builder.addUnicodeLocaleAttribute(value) }
        fun removeUnicodeLocaleAttribute(value: String?): Builder = mutate { builder.removeUnicodeLocaleAttribute(value) }
        fun clear(): Builder { builder.clear(); return this }
        fun clearExtensions(): Builder { builder.clearExtensions(); return this }
        fun build(): ULocale = getInstance(builder.getBaseLocale(), builder.getLocaleExtensions())
    }
    enum class AvailableType { DEFAULT, ONLY_LEGACY_ALIASES, WITH_LEGACY_ALIASES }
    companion object {
        const val PRIVATE_USE_EXTENSION = 'x'
        const val UNICODE_LOCALE_EXTENSION = 'u'
        private val nameCache = object : SoftCache<String, String, Unit?>() { override fun createInstance(key: String, data: Unit?): String = LocaleIDParser(key).getName() }
        val ROOT = ULocale("")
        val ENGLISH = ULocale("en"); val FRENCH = ULocale("fr"); val GERMAN = ULocale("de"); val ITALIAN = ULocale("it")
        val JAPANESE = ULocale("ja"); val KOREAN = ULocale("ko"); val CHINESE = ULocale("zh")
        val SIMPLIFIED_CHINESE = ULocale("zh_Hans"); val TRADITIONAL_CHINESE = ULocale("zh_Hant")
        val FRANCE = ULocale("fr_FR"); val GERMANY = ULocale("de_DE"); val ITALY = ULocale("it_IT"); val JAPAN = ULocale("ja_JP"); val KOREA = ULocale("ko_KR")
        val CHINA = ULocale("zh_Hans_CN"); val PRC = CHINA; val TAIWAN = ULocale("zh_Hant_TW")
        val UK = ULocale("en_GB"); val US = ULocale("en_US"); val CANADA = ULocale("en_CA"); val CANADA_FRENCH = ULocale("fr_CA")
        fun getName(localeID: String): String {
            var input = localeID
            if (!localeID.contains('@') && shortestSubtagLength(localeID) == 1) {
                if ('_' in localeID && localeID[1] != '_' && localeID[1] != '-') input = localeID.replace('_', '-')
                input = forLanguageTag(input).getName()
                if (input.isEmpty()) input = localeID
            } else if (localeID.equals("root", ignoreCase = true)) input = ""
            else if (localeID.length >= 3 && localeID.regionMatches(0, "und", 0, 3, ignoreCase = true)) {
                if (localeID.length == 3) input = "" else if (localeID[3] == '-' || localeID[3] == '_') input = localeID.substring(3)
            }
            return nameCache.getInstance(input, null)!!
        }
        private fun shortestSubtagLength(id: String): Int {
            var length = id.length; var reset = true; var count = 0
            for (c in id) if (c != '_' && c != '-') { if (reset) { reset = false; count = 0 }; count++ }
            else { if (count != 0 && count < length) length = count; reset = true }
            return length
        }
        private fun lscvToID(language: String?, script: String?, country: String?, variant: String?): String = buildString {
            if (!language.isNullOrEmpty()) append(language)
            if (!script.isNullOrEmpty()) append('_').append(script)
            if (!country.isNullOrEmpty()) append('_').append(country)
            if (!variant.isNullOrEmpty()) { if (country.isNullOrEmpty()) append('_'); append('_').append(variant) }
        }
        fun getLanguage(id: String): String = LocaleIDParser(id).getLanguage()
        fun getScript(id: String): String = LocaleIDParser(id).getScript()
        fun getCountry(id: String): String = LocaleIDParser(id).getCountry()
        fun getVariant(id: String): String = LocaleIDParser(id).getVariant()
        fun getBaseName(id: String): String = if ('@' !in id) id else LocaleIDParser(id).getBaseName()
        fun getKeywords(id: String): MutableIterator<String>? = LocaleIDParser(id).getKeywords()
        fun getKeywordValue(id: String, key: String?): String? = LocaleIDParser(id).getKeywordValue(key)
        fun setKeywordValue(id: String, key: String?, value: String?): String = LocaleIDParser(id).also { it.setKeywordValue(key, value) }.getName()
        fun getISO3Language(id: String): String = LocaleIDs.getISO3Language(getLanguage(id))
        fun getISO3Country(id: String): String = LocaleIDs.getISO3Country(getCountry(id))
        fun getISOLanguages(): Array<String> = LocaleIDs.getISOLanguages()
        fun getISOCountries(): Array<String> = LocaleIDs.getISOCountries()
        fun getFallback(id: String): String = fallbackString(getName(id))
        private fun fallbackString(id: String): String {
            val extensions = id.indexOf('@').let { if (it < 0) id.length else it }
            var last = id.lastIndexOf('_', extensions)
            if (last < 0) last = 0 else while (last > 0 && id[last - 1] == '_') last--
            return id.substring(0, last) + id.substring(extensions)
        }
        fun toUnicodeLocaleKey(key: String): String? = KeyTypeData.toBcpKey(key) ?: if (UnicodeLocaleExtension.isKey(key)) AsciiUtil.toLowerString(key) else null
        fun toUnicodeLocaleType(key: String, value: String): String? = KeyTypeData.toBcpType(key, value) ?: if (UnicodeLocaleExtension.isType(value)) AsciiUtil.toLowerString(value) else null
        fun toLegacyKey(key: String): String? = KeyTypeData.toLegacyKey(key) ?: if (key.isNotEmpty() && AsciiUtil.isAlphaNumericString(key)) AsciiUtil.toLowerString(key) else null
        fun toLegacyType(key: String, value: String): String? = KeyTypeData.toLegacyType(key, value) ?: if (value.split('_', '/', '-').all { it.isNotEmpty() && AsciiUtil.isAlphaNumericString(it) }) AsciiUtil.toLowerString(value) else null
        fun forLanguageTag(tag: String): ULocale {
            val builder = InternalLocaleBuilder().setLanguageTag(LanguageTag.parse(tag))
            return getInstance(builder.getBaseLocale(), builder.getLocaleExtensions())
        }
        private fun getInstance(base: BaseLocale, extensions: LocaleExtensions): ULocale {
            var id = lscvToID(base.getLanguage(), base.getScript(), base.getRegion(), base.getVariant())
            val keywords = HashMap<String, String>()
            for (key in extensions.getKeys()) {
                val extension = extensions.getExtension(key)!!
                if (extension is UnicodeLocaleExtension) {
                    for (unicodeKey in extension.getUnicodeLocaleKeys()) {
                        val type = extension.getUnicodeLocaleType(unicodeKey)!!
                        val legacyKey = toLegacyKey(unicodeKey)!!
                        val legacyType = toLegacyType(unicodeKey, type.ifEmpty { "yes" })!!
                        if (legacyKey == "va" && legacyType == "posix" && base.getVariant().isEmpty()) id += "_POSIX"
                        else keywords[legacyKey] = legacyType
                    }
                    if (extension.getUnicodeLocaleAttributes().isNotEmpty()) keywords["attribute"] = extension.getUnicodeLocaleAttributes().joinToString("-")
                } else keywords[key.toString()] = extension.getValue()!!
            }
            if (keywords.isNotEmpty()) id += keywords.keys.sorted().joinToString(";", "@") { "$it=${keywords.getValue(it)}" }
            return ULocale(id)
        }
    }
}
