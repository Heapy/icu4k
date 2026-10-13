// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2011, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.AsciiUtil

/** Original ICU/POSIX locale ID parsing and mutable keyword normalization. */
class LocaleIDParser(localeID: String, private val canonicalize: Boolean = false) {
    private val id = localeID.toCharArray()
    private var index = 0
    private var buffer = StringBuilder(id.size + 5)
    private var hadCountry = false
    private var keywords: MutableMap<String, String>? = null
    private var baseName: String? = null
    private fun reset() { index = 0; buffer = StringBuilder(id.size + 5) }
    private fun set(position: Int, value: String) { buffer.deleteRange(position, buffer.length); buffer.insert(position, value) }
    private fun next(): Char = if (index == id.size) { index++; DONE } else id[index++]
    private fun isTerminator(c: Char): Boolean = c == '@' || c == DONE || c == '.'
    private fun isTerminatorOrIDSeparator(c: Char): Boolean = c == '_' || c == '-' || isTerminator(c)
    private fun atTerminator(): Boolean = index >= id.size || isTerminator(id[index])
    private fun skipUntilTerminatorOrIDSeparator() { while (!isTerminatorOrIDSeparator(next())) { }; index-- }
    private fun haveExperimentalLanguagePrefix(): Boolean = id.size > 2 && (id[1] == '-' || id[1] == '_') && id[0] in "xXiI"
    private fun haveKeywordAssign(): Boolean = (index until id.size).any { id[it] == '=' }
    private fun parseLanguage(): Int {
        val startLength = buffer.length
        if (haveExperimentalLanguagePrefix()) { buffer.append(AsciiUtil.toLower(id[0])).append('-'); index = 2 }
        while (true) { val c = next(); if (isTerminatorOrIDSeparator(c)) break; buffer.append(AsciiUtil.toLower(c)) }
        index--
        if (buffer.length - startLength == 3) LocaleIDs.threeToTwoLetterLanguage(buffer.substring(0))?.let { set(0, it) }
        return 0
    }
    private fun skipLanguage() { if (haveExperimentalLanguagePrefix()) index = 2; skipUntilTerminatorOrIDSeparator() }
    private fun parseScript(): Int {
        if (atTerminator()) return buffer.length
        val oldIndex = index++
        var oldLength = buffer.length
        var first = true
        while (true) {
            val c = next()
            if (isTerminatorOrIDSeparator(c) || !AsciiUtil.isAlpha(c)) break
            if (first) { buffer.append('_').append(AsciiUtil.toUpper(c)); first = false } else buffer.append(AsciiUtil.toLower(c))
        }
        index--
        if (index - oldIndex != 5) { index = oldIndex; buffer.deleteRange(oldLength, buffer.length) } else oldLength++
        return oldLength
    }
    private fun skipScript() {
        if (!atTerminator()) {
            val old = index++
            while (true) { val c = next(); if (isTerminatorOrIDSeparator(c) || !AsciiUtil.isAlpha(c)) break }
            index--; if (index - old != 5) index = old
        }
    }
    private fun parseCountry(): Int {
        if (atTerminator()) return buffer.length
        val oldIndex = index++
        var oldLength = buffer.length
        var first = true
        while (true) {
            val c = next(); if (isTerminatorOrIDSeparator(c)) break
            if (first) { hadCountry = true; buffer.append('_'); oldLength++; first = false }
            buffer.append(AsciiUtil.toUpper(c))
        }
        index--
        val count = buffer.length - oldLength
        if (count != 0 && (count < 2 || count > 3)) {
            index = oldIndex; oldLength--; buffer.deleteRange(oldLength, buffer.length); hadCountry = false
        } else if (count == 3) LocaleIDs.threeToTwoLetterRegion(buffer.substring(oldLength))?.let { set(oldLength, it) }
        return oldLength
    }
    private fun skipCountry() {
        if (!atTerminator()) {
            if (id[index] == '_' || id[index] == '-') index++
            val old = index
            skipUntilTerminatorOrIDSeparator()
            if (index - old < 2 || index - old > 3) index = old
        }
    }
    private fun parseVariant(): Int {
        var oldLength = buffer.length
        var start = true; var separator = true; var skipping = false; var first = true
        while (true) {
            var c = next(); if (c == DONE) break
            when {
                c == '.' -> { start = false; skipping = true }
                c == '@' -> {
                    if (haveKeywordAssign()) break
                    skipping = false; start = false; separator = true
                }
                start -> { start = false; if (c != '_' && c != '-') index-- }
                !skipping -> {
                    if (separator) {
                        separator = false
                        if (first && !hadCountry) { buffer.append('_'); oldLength++ }
                        buffer.append('_'); if (first) { oldLength++; first = false }
                    }
                    c = AsciiUtil.toUpper(c)
                    if (c == '-' || c == ',') c = '_'
                    buffer.append(c)
                    if (buffer.length - oldLength > 179) throw IllegalArgumentException("variants is too long")
                }
            }
        }
        index--; return oldLength
    }
    fun getLanguage(): String { reset(); return buffer.substring(parseLanguage()) }
    fun getScript(): String { reset(); skipLanguage(); return buffer.substring(parseScript()) }
    fun getCountry(): String { reset(); skipLanguage(); skipScript(); return buffer.substring(parseCountry()) }
    fun getVariant(): String { reset(); skipLanguage(); skipScript(); skipCountry(); return buffer.substring(parseVariant()) }
    fun getLanguageScriptCountryVariant(): Array<String> {
        reset()
        return arrayOf(buffer.substring(parseLanguage()), buffer.substring(parseScript()), buffer.substring(parseCountry()), buffer.substring(parseVariant()))
    }
    fun setBaseName(baseName: String?) { this.baseName = baseName }
    fun parseBaseName() {
        val override = baseName
        if (override != null) set(0, override) else {
            reset(); parseLanguage(); parseScript(); parseCountry(); parseVariant()
            if (buffer.isNotEmpty() && buffer.last() == '_') buffer.deleteAt(buffer.lastIndex)
        }
    }
    fun getBaseName(): String { baseName?.let { return it }; parseBaseName(); return buffer.substring(0) }
    fun getName(): String { parseBaseName(); parseKeywords(); return buffer.substring(0) }
    private fun setToKeywordStart(): Boolean {
        var i = index
        while (i < id.size) {
            if (id[i] == '@') {
                i++
                if (canonicalize) {
                    for (j in i until id.size) if (id[j] == '=') { index = i; return true }
                } else if (i < id.size) { index = i; return true }
                break
            }
            i++
        }
        return false
    }
    private fun javaTrim(s: String): String = s.trim { it.code <= 0x20 }
    private fun getKeyword(): String {
        val start = index
        while (true) { val c = next(); if (c == DONE || c == '=') break }
        index--; return AsciiUtil.toLowerString(javaTrim(id.concatToString(start, index)))
    }
    private fun getValue(): String {
        val start = index
        while (true) { val c = next(); if (c == DONE || c == ';') break }
        index--; return javaTrim(id.concatToString(start, index))
    }
    fun getKeywordMap(): MutableMap<String, String> {
        if (keywords == null) {
            var map: LocaleKeywordMap? = null
            if (setToKeywordStart()) {
                do {
                    val key = getKeyword(); if (key.isEmpty()) break
                    val c = next()
                    if (c != '=') { if (c == DONE) break else continue }
                    val value = getValue(); if (value.isEmpty()) continue
                    if (map == null) map = LocaleKeywordMap() else if (map.containsKey(key)) continue
                    map[key] = value
                } while (next() == ';')
            }
            keywords = map ?: LocaleEmptyKeywordMap
        }
        return keywords!!
    }
    private fun parseKeywords(): Int {
        var oldLength = buffer.length
        val map = getKeywordMap()
        if (map.isNotEmpty()) {
            var first = true
            for ((key, value) in map) { buffer.append(if (first) '@' else ';'); first = false; buffer.append(key).append('=').append(value) }
            if (!first) oldLength++
        }
        return oldLength
    }
    fun getKeywords(): MutableIterator<String>? = getKeywordMap().let { if (it.isEmpty()) null else it.keys.iterator() }
    fun getKeywordValue(keywordName: String?): String? = getKeywordMap().let { if (it.isEmpty()) null else it[AsciiUtil.toLowerString(javaTrim(keywordName!!))] }
    fun defaultKeywordValue(keywordName: String?, value: String?) { setKeywordValue(keywordName, value, false) }
    fun setKeywordValue(keywordName: String?, value: String?) { setKeywordValue(keywordName, value, true) }
    private fun setKeywordValue(name: String?, rawValue: String?, reset: Boolean) {
        if (name == null) { if (reset) keywords = LocaleEmptyKeywordMap; return }
        val key = AsciiUtil.toLowerString(javaTrim(name))
        if (key.isEmpty()) throw IllegalArgumentException("keyword must not be empty")
        val value = rawValue?.let(::javaTrim)
        if (value != null && value.isEmpty()) throw IllegalArgumentException("value must not be empty")
        val map = getKeywordMap()
        if (map.isEmpty()) {
            if (value != null) { keywords = LocaleKeywordMap().also { it[key] = javaTrim(value) } }
        } else if (reset || !map.containsKey(key)) {
            if (value != null) map[key] = value else { map.remove(key); if (map.isEmpty()) keywords = LocaleEmptyKeywordMap }
        }
    }
    companion object { private const val DONE = '\uffff' }
}
