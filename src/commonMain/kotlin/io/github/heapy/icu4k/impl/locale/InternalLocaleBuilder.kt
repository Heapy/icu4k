// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

/** Original locale builder validation, mutation, parsing and base/extension conversion. */
class InternalLocaleBuilder {
    private var _language = ""
    private var _script = ""
    private var _region = ""
    private var _variant = ""
    private var _extensions: HashMap<CaseInsensitiveChar, String>? = null
    private var _uattributes: HashSet<CaseInsensitiveString>? = null
    private var _ukeywords: HashMap<CaseInsensitiveString, String>? = null
    fun setLanguage(language: String?): InternalLocaleBuilder {
        if (language.isNullOrEmpty()) _language = "" else {
            if (!LanguageTag.isLanguage(language)) throw LocaleSyntaxException("Ill-formed language: $language", 0)
            _language = language
        }
        return this
    }
    fun setScript(script: String?): InternalLocaleBuilder {
        if (script.isNullOrEmpty()) _script = "" else {
            if (!LanguageTag.isScript(script)) throw LocaleSyntaxException("Ill-formed script: $script", 0)
            _script = script
        }
        return this
    }
    fun setRegion(region: String?): InternalLocaleBuilder {
        if (region.isNullOrEmpty()) _region = "" else {
            if (!LanguageTag.isRegion(region)) throw LocaleSyntaxException("Ill-formed region: $region", 0)
            _region = region
        }
        return this
    }
    fun setVariant(variant: String?): InternalLocaleBuilder {
        if (variant.isNullOrEmpty()) _variant = "" else {
            val normalized = variant.replace('-', '_')
            val itr = StringTokenIterator(normalized, "_")
            while (!itr.isDone()) {
                if (!LanguageTag.isVariant(itr.current()!!)) throw LocaleSyntaxException("Ill-formed variant: $variant", itr.currentStart())
                itr.next()
            }
            _variant = normalized
        }
        return this
    }
    fun addUnicodeLocaleAttribute(attribute: String?): InternalLocaleBuilder {
        if (attribute == null || !UnicodeLocaleExtension.isAttribute(attribute)) throw LocaleSyntaxException("Ill-formed Unicode locale attribute: $attribute")
        if (_uattributes == null) _uattributes = HashSet(4)
        _uattributes!!.add(CaseInsensitiveString(attribute))
        return this
    }
    fun removeUnicodeLocaleAttribute(attribute: String?): InternalLocaleBuilder {
        if (attribute == null || !UnicodeLocaleExtension.isAttribute(attribute)) throw LocaleSyntaxException("Ill-formed Unicode locale attribute: $attribute")
        _uattributes?.remove(CaseInsensitiveString(attribute))
        return this
    }
    fun setUnicodeLocaleKeyword(key: String, type: String?): InternalLocaleBuilder {
        if (!UnicodeLocaleExtension.isKey(key)) throw LocaleSyntaxException("Ill-formed Unicode locale keyword key: $key")
        val cikey = CaseInsensitiveString(key)
        if (type == null) _ukeywords?.remove(cikey) else {
            if (type.isNotEmpty()) {
                val itr = StringTokenIterator(type.replace('_', '-'), "-")
                while (!itr.isDone()) {
                    if (!UnicodeLocaleExtension.isTypeSubtag(itr.current()!!)) throw LocaleSyntaxException("Ill-formed Unicode locale keyword type: $type", itr.currentStart())
                    itr.next()
                }
            }
            if (_ukeywords == null) _ukeywords = HashMap(4)
            _ukeywords!![cikey] = type // Upstream validates normalized separators but retains original type.
        }
        return this
    }
    fun setExtension(singleton: Char, value: String?): InternalLocaleBuilder {
        val privateuse = LanguageTag.isPrivateusePrefixChar(singleton)
        if (!privateuse && !LanguageTag.isExtensionSingletonChar(singleton)) throw LocaleSyntaxException("Ill-formed extension key: $singleton")
        val key = CaseInsensitiveChar(singleton)
        if (value.isNullOrEmpty()) {
            if (UnicodeLocaleExtension.isSingletonChar(key.value())) { _uattributes?.clear(); _ukeywords?.clear() }
            else if (_extensions?.containsKey(key) == true) _extensions!!.remove(key)
        } else {
            val normalized = value.replace('_', '-')
            val itr = StringTokenIterator(normalized, "-")
            while (!itr.isDone()) {
                val s = itr.current()!!
                val valid = if (privateuse) LanguageTag.isPrivateuseSubtag(s) else LanguageTag.isExtensionSubtag(s)
                if (!valid) throw LocaleSyntaxException("Ill-formed extension value: $s", itr.currentStart())
                itr.next()
            }
            if (UnicodeLocaleExtension.isSingletonChar(key.value())) setUnicodeLocaleExtension(normalized) else {
                if (_extensions == null) _extensions = HashMap(4)
                _extensions!![key] = normalized
            }
        }
        return this
    }
    fun setExtensions(subtags: String?): InternalLocaleBuilder {
        if (subtags.isNullOrEmpty()) return clearExtensions()
        val input = subtags.replace('_', '-')
        val itr = StringTokenIterator(input, "-")
        val extensions = ArrayList<String>()
        var privateuse: String? = null
        var parsed = 0
        while (!itr.isDone() && LanguageTag.isExtensionSingleton(itr.current()!!)) {
            val start = itr.currentStart()
            val singleton = itr.current()!!
            val extension = StringBuilder(singleton)
            itr.next()
            while (!itr.isDone() && LanguageTag.isExtensionSubtag(itr.current()!!)) {
                extension.append('-').append(itr.current()); parsed = itr.currentEnd(); itr.next()
            }
            if (parsed < start) throw LocaleSyntaxException("Incomplete extension '$singleton'", start)
            extensions.add(extension.toString())
        }
        if (!itr.isDone() && LanguageTag.isPrivateusePrefix(itr.current()!!)) {
            val start = itr.currentStart()
            val value = StringBuilder(itr.current()!!)
            itr.next()
            while (!itr.isDone() && LanguageTag.isPrivateuseSubtag(itr.current()!!)) {
                value.append('-').append(itr.current()); parsed = itr.currentEnd(); itr.next()
            }
            if (parsed <= start) throw LocaleSyntaxException("Incomplete privateuse:" + input.substring(start), start)
            privateuse = value.toString()
        }
        if (!itr.isDone()) throw LocaleSyntaxException("Ill-formed extension subtags:" + input.substring(itr.currentStart()), itr.currentStart())
        return setExtensions(extensions, privateuse)
    }
    private fun setExtensions(extensions: List<String>, privateuse: String?): InternalLocaleBuilder {
        clearExtensions()
        // Original processedExtensions set is never populated, so later duplicates overwrite.
        for (extension in extensions) {
            val key = CaseInsensitiveChar(extension[0])
            if (UnicodeLocaleExtension.isSingletonChar(key.value())) setUnicodeLocaleExtension(extension.substring(2)) else {
                if (_extensions == null) _extensions = HashMap(4)
                _extensions!![key] = extension.substring(2)
            }
        }
        if (!privateuse.isNullOrEmpty()) {
            if (_extensions == null) _extensions = HashMap(1)
            _extensions!![CaseInsensitiveChar(privateuse[0])] = privateuse.substring(2)
        }
        return this
    }
    fun setLanguageTag(tag: LanguageTag): InternalLocaleBuilder {
        clear()
        if (tag.getExtlangs().isNotEmpty()) _language = tag.getExtlangs()[0]
        else if (tag.getLanguage() != LanguageTag.UNDETERMINED) _language = tag.getLanguage()
        _script = tag.getScript(); _region = tag.getRegion()
        _variant = tag.getVariants().sorted().joinToString("_")
        return setExtensions(tag.getExtensions(), tag.getPrivateuse())
    }
    fun clear(): InternalLocaleBuilder {
        _language = ""; _script = ""; _region = ""; _variant = ""
        return clearExtensions()
    }
    fun clearExtensions(): InternalLocaleBuilder { _extensions?.clear(); _uattributes?.clear(); _ukeywords?.clear(); return this }
    fun getBaseLocale(): BaseLocale {
        var variant = _variant
        val privateuse = _extensions?.get(PRIVUSE_KEY)
        if (privateuse != null) {
            val itr = StringTokenIterator(privateuse, LanguageTag.SEP)
            var sawPrefix = false
            var start = -1
            while (!itr.isDone()) {
                if (sawPrefix) { start = itr.currentStart(); break }
                if (AsciiUtil.caseIgnoreMatch(itr.current()!!, LanguageTag.PRIVUSE_VARIANT_PREFIX)) sawPrefix = true
                itr.next()
            }
            if (start != -1) {
                if (variant.isNotEmpty()) variant += BaseLocale.SEP
                variant += privateuse.substring(start).replace(LanguageTag.SEP, BaseLocale.SEP)
            }
        }
        return BaseLocale.getInstance(_language, _script, _region, variant)
    }
    fun setLocale(base: BaseLocale, extensions: LocaleExtensions?): InternalLocaleBuilder {
        val language = base.getLanguage(); val script = base.getScript(); val region = base.getRegion(); val variant = base.getVariant()
        if (language.isNotEmpty() && !LanguageTag.isLanguage(language)) throw LocaleSyntaxException("Ill-formed language: $language")
        if (script.isNotEmpty() && !LanguageTag.isScript(script)) throw LocaleSyntaxException("Ill-formed script: $script")
        if (region.isNotEmpty() && !LanguageTag.isRegion(region)) throw LocaleSyntaxException("Ill-formed region: $region")
        if (variant.isNotEmpty()) {
            val itr = StringTokenIterator(variant, BaseLocale.SEP)
            while (!itr.isDone()) {
                if (!LanguageTag.isVariant(itr.current()!!)) throw LocaleSyntaxException("Ill-formed variant: $variant", itr.currentStart())
                itr.next()
            }
        }
        _language = language; _script = script; _region = region; _variant = variant
        clearExtensions()
        if (extensions != null) for (key in extensions.getKeys()) {
            val extension = extensions.getExtension(key)!!
            if (extension is UnicodeLocaleExtension) {
                for (attribute in extension.getUnicodeLocaleAttributes()) {
                    if (_uattributes == null) _uattributes = HashSet(4)
                    _uattributes!!.add(CaseInsensitiveString(attribute))
                }
                for (keyword in extension.getUnicodeLocaleKeys()) {
                    if (_ukeywords == null) _ukeywords = HashMap(4)
                    _ukeywords!![CaseInsensitiveString(keyword)] = extension.getUnicodeLocaleType(keyword)!!
                }
            } else {
                if (_extensions == null) _extensions = HashMap(4)
                _extensions!![CaseInsensitiveChar(key)] = extension.getValue()!!
            }
        }
        return this
    }
    fun getLocaleExtensions(): LocaleExtensions {
        if (_extensions.isNullOrEmpty() && _uattributes.isNullOrEmpty() && _ukeywords.isNullOrEmpty()) return LocaleExtensions.EMPTY_EXTENSIONS
        return LocaleExtensions(_extensions, _uattributes, _ukeywords)
    }
    private fun setUnicodeLocaleExtension(subtags: String) {
        _uattributes?.clear(); _ukeywords?.clear()
        val itr = StringTokenIterator(subtags, "-")
        while (!itr.isDone() && UnicodeLocaleExtension.isAttribute(itr.current()!!)) {
            if (_uattributes == null) _uattributes = HashSet(4)
            _uattributes!!.add(CaseInsensitiveString(itr.current()!!)); itr.next()
        }
        var key: CaseInsensitiveString? = null
        var typeStart = -1; var typeEnd = -1
        fun emit() {
            if (_ukeywords == null) _ukeywords = HashMap(4)
            _ukeywords!![key!!] = if (typeStart == -1) "" else subtags.substring(typeStart, typeEnd)
        }
        while (!itr.isDone()) {
            val s = itr.current()!!
            if (key != null) {
                if (UnicodeLocaleExtension.isKey(s)) {
                    emit()
                    val next = CaseInsensitiveString(s)
                    key = if (_ukeywords!!.containsKey(next)) null else next
                    typeStart = -1; typeEnd = -1
                } else { if (typeStart == -1) typeStart = itr.currentStart(); typeEnd = itr.currentEnd() }
            } else if (UnicodeLocaleExtension.isKey(s)) {
                val next = CaseInsensitiveString(s)
                key = if (_ukeywords?.containsKey(next) == true) null else next
            }
            if (!itr.hasNext()) { if (key != null) emit(); break }
            itr.next()
        }
    }
    class CaseInsensitiveString(private val s: String) {
        fun value(): String = s
        override fun hashCode(): Int = AsciiUtil.toLowerString(s).hashCode()
        override fun equals(other: Any?): Boolean = this === other || other is CaseInsensitiveString && AsciiUtil.caseIgnoreMatch(s, other.s)
    }
    class CaseInsensitiveChar(private val c: Char) {
        fun value(): Char = c
        override fun hashCode(): Int = AsciiUtil.toLower(c).code
        // Preserve ICU4J's asymmetric uppercase-key comparison.
        override fun equals(other: Any?): Boolean = this === other || other is CaseInsensitiveChar && c == AsciiUtil.toLower(other.c)
    }
    companion object {
        private val PRIVUSE_KEY = CaseInsensitiveChar(LanguageTag.PRIVATEUSE[0])
        fun removePrivateuseVariant(value: String): String? {
            val itr = StringTokenIterator(value, "-")
            var prefixStart = -1
            var sawVariant = false
            while (!itr.isDone()) {
                if (prefixStart != -1) { sawVariant = true; break }
                if (AsciiUtil.caseIgnoreMatch(itr.current()!!, LanguageTag.PRIVUSE_VARIANT_PREFIX)) prefixStart = itr.currentStart()
                itr.next()
            }
            if (!sawVariant) return value
            return if (prefixStart == 0) null else value.substring(0, prefixStart - 1)
        }
    }
}
