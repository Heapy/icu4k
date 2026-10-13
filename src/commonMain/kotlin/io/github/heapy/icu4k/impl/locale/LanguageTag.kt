// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010-2013, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.impl.CaseMapImpl
import io.github.heapy.icu4k.impl.UCaseProps

/** ICU language-tag parser. Conversion from BaseLocale/LocaleExtensions is still pending. */
class LanguageTag private constructor() {
    private var language = ""
    private var script = ""
    private var region = ""
    private var privateuse = ""
    private val extlangs = ArrayList<String>()
    private val variants = ArrayList<String>()
    private val extensions = ArrayList<String>()
    fun getLanguage(): String = language
    fun getScript(): String = script
    fun getRegion(): String = region
    fun getPrivateuse(): String = privateuse
    fun getExtlangs(): List<String> = extlangs.toList()
    fun getVariants(): List<String> = variants.toList()
    fun getExtensions(): List<String> = extensions.toList()
    override fun toString(): String = buildString {
        if (language.isNotEmpty()) {
            append(language)
            for (s in extlangs) append(SEP).append(s)
            if (script.isNotEmpty()) append(SEP).append(script)
            if (region.isNotEmpty()) append(SEP).append(region)
            for (s in variants) append(SEP).append(s)
            for (s in extensions) append(SEP).append(s)
        }
        if (privateuse.isNotEmpty()) { if (isNotEmpty()) append(SEP); append(privateuse) }
    }
    companion object {
        const val SEP = "-"
        const val PRIVATEUSE = "x"
        var UNDETERMINED = "und"
        const val PRIVUSE_VARIANT_PREFIX = "lvariant"
        private val legacy = mapOf(
            "art-lojban" to "jbo", "cel-gaulish" to "xtg", "en-gb-oed" to "en-GB-x-oed",
            "i-ami" to "ami", "i-bnn" to "bnn", "i-default" to "en-x-i-default", "i-enochian" to "und-x-i-enochian",
            "i-hak" to "hak", "i-klingon" to "tlh", "i-lux" to "lb", "i-mingo" to "see-x-i-mingo", "i-navajo" to "nv",
            "i-pwn" to "pwn", "i-tao" to "tao", "i-tay" to "tay", "i-tsu" to "tsu", "no-bok" to "nb", "no-nyn" to "nn",
            "sgn-be-fr" to "sfb", "sgn-be-nl" to "vgt", "sgn-ch-de" to "sgg", "zh-guoyu" to "cmn", "zh-hakka" to "hak",
            "zh-min" to "nan-x-zh-min", "zh-min-nan" to "nan", "zh-xiang" to "hsn")
        fun parse(languageTag: String, sts: ParseStatus? = null): LanguageTag {
            val status = sts ?: ParseStatus()
            status.reset()
            var mapping = legacy[AsciiUtil.toLowerString(languageTag)]
            var dash = 2
            while (mapping == null) {
                dash = languageTag.indexOf('-', dash + 1)
                if (dash < 0) break
                mapping = legacy[AsciiUtil.toLowerString(languageTag.substring(0, dash))]
            }
            val input = if (mapping == null) languageTag else mapping + if (dash < 0 || legacy.containsKey(AsciiUtil.toLowerString(languageTag))) "" else languageTag.substring(dash)
            val itr = StringTokenIterator(input, SEP)
            val tag = LanguageTag()
            fun consume(): String {
                val result = itr.current()!!
                status._parseLength = itr.currentEnd()
                itr.next()
                return result
            }
            if (!itr.isDone() && isLanguage(itr.current()!!)) {
                tag.language = consume()
                if (tag.language.length <= 3) while (!itr.isDone() && isExtlang(itr.current()!!) && tag.extlangs.size < 3) tag.extlangs.add(consume())
                if (!itr.isDone() && isScript(itr.current()!!)) tag.script = consume()
                if (!itr.isDone() && isRegion(itr.current()!!)) tag.region = consume()
                while (!itr.isDone() && isVariant(itr.current()!!)) {
                    val variant = CaseMapImpl.toUpper(UCaseProps.getCaseLocale(defaultLocaleLanguage()), 0, consume())
                    if (!tag.variants.contains(variant)) tag.variants.add(variant)
                }
                while (!itr.isDone() && isExtensionSingleton(itr.current()!!)) {
                    val start = itr.currentStart()
                    val singleton = CaseMapImpl.toLower(UCaseProps.getCaseLocale(defaultLocaleLanguage()), 0, itr.current()!!)
                    val extension = StringBuilder(singleton)
                    itr.next()
                    while (!itr.isDone() && isExtensionSubtag(itr.current()!!)) extension.append(SEP).append(consume())
                    if (status._parseLength <= start) {
                        status._errorIndex = start; status._errorMsg = "Incomplete extension '$singleton'"
                        break
                    }
                    if (tag.extensions.none { it[0] == singleton[0] }) tag.extensions.add(extension.toString())
                }
            }
            if (!itr.isDone() && !status.isError() && isPrivateusePrefix(itr.current()!!)) {
                val start = itr.currentStart()
                val privateuse = StringBuilder(itr.current()!!)
                itr.next()
                while (!itr.isDone() && isPrivateuseSubtag(itr.current()!!)) privateuse.append(SEP).append(consume())
                if (status._parseLength <= start) { status._errorIndex = start; status._errorMsg = "Incomplete privateuse" }
                else tag.privateuse = privateuse.toString()
            }
            if (mapping != null) status._parseLength = languageTag.length
            else if (!itr.isDone() && !status.isError()) {
                val token = itr.current()!!
                status._errorIndex = itr.currentStart()
                status._errorMsg = if (token.isEmpty()) "Empty subtag" else "Invalid subtag: $token"
            }
            return tag
        }
        fun isLanguage(s: String): Boolean = s.length in 2..8 && AsciiUtil.isAlphaString(s)
        fun isExtlang(s: String): Boolean = s.length == 3 && AsciiUtil.isAlphaString(s)
        fun isScript(s: String): Boolean = s.length == 4 && AsciiUtil.isAlphaString(s)
        fun isRegion(s: String): Boolean = s.length == 2 && AsciiUtil.isAlphaString(s) || s.length == 3 && AsciiUtil.isNumericString(s)
        fun isVariant(s: String): Boolean = when (s.length) {
            in 5..8 -> AsciiUtil.isAlphaNumericString(s)
            4 -> AsciiUtil.isNumeric(s[0]) && s.drop(1).all(AsciiUtil::isAlphaNumeric)
            else -> false
        }
        fun isTKey(s: String): Boolean = s.length == 2 && AsciiUtil.isAlpha(s[0]) && AsciiUtil.isNumeric(s[1])
        fun isExtensionSingleton(s: String): Boolean = s.length == 1 && AsciiUtil.isAlphaNumericString(s) && !AsciiUtil.caseIgnoreMatch(PRIVATEUSE, s)
        fun isExtensionSingletonChar(c: Char): Boolean = isExtensionSingleton(c.toString())
        fun isExtensionSubtag(s: String): Boolean = s.length in 2..8 && AsciiUtil.isAlphaNumericString(s)
        fun isPrivateusePrefix(s: String): Boolean = s.length == 1 && AsciiUtil.caseIgnoreMatch(PRIVATEUSE, s)
        fun isPrivateusePrefixChar(c: Char): Boolean = AsciiUtil.caseIgnoreMatch(PRIVATEUSE, c.toString())
        fun isPrivateuseSubtag(s: String): Boolean = s.length in 1..8 && AsciiUtil.isAlphaNumericString(s)
        fun canonicalizeLanguage(s: String): String = AsciiUtil.toLowerString(s)
        fun canonicalizeExtlang(s: String): String = AsciiUtil.toLowerString(s)
        fun canonicalizeScript(s: String): String = AsciiUtil.toTitleString(s)
        fun canonicalizeRegion(s: String): String = AsciiUtil.toUpperString(s)
        fun canonicalizeVariant(s: String): String = AsciiUtil.toLowerString(s)
        fun canonicalizeExtension(s: String): String {
            var result = AsciiUtil.toLowerString(s)
            if (result.startsWith("u-")) {
                for (word in listOf("true", "yes")) {
                    val suffix = "-$word"
                    while (result.endsWith(suffix)) result = result.dropLast(suffix.length)
                    while (true) {
                        val found = result.indexOf("$suffix-")
                        if (found <= 0) break
                        result = result.substring(0, found) + result.substring(found + suffix.length)
                    }
                }
            }
            return result
        }
        fun canonicalizeExtensionSingleton(s: String): String = AsciiUtil.toLowerString(s)
        fun canonicalizeExtensionSubtag(s: String): String = AsciiUtil.toLowerString(s)
        fun canonicalizePrivateuse(s: String): String = AsciiUtil.toLowerString(s)
        fun canonicalizePrivateuseSubtag(s: String): String = AsciiUtil.toLowerString(s)
    }
}
