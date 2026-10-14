// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.*

/** CLDR likely language/script/region lookup over ICU's shared compact byte trie. */
class LikelySubtags private constructor(data: Data) {
    class Data(val languageAliases: Map<String, String>, val regionAliases: Map<String, String>,
        val trie: ByteArray, val lsrs: Array<LSR>) {
        override fun equals(other: Any?): Boolean = this === other || other is Data &&
            languageAliases == other.languageAliases && regionAliases == other.regionAliases &&
            trie.contentEquals(other.trie) && lsrs.contentEquals(other.lsrs)
        override fun hashCode(): Int = 1
        companion object {
            fun load(): Data {
                val bundle = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "langInfo",
                    defaultICUResourceLoader(), ICUResourceBundle.OpenType.DIRECT)
                val value = bundle.getValueWithFallback("likely")
                val table = value.getTable()
                fun required(key: String): UResource.Value {
                    if (!table.findValue(key, value)) throw MissingResourceException("langInfo.res missing data", "", "likely/$key")
                    return value
                }
                fun aliases(key: String): Map<String, String> {
                    if (!table.findValue(key, value)) return emptyMap()
                    val pairs = value.getStringArray()
                    val result = HashMap<String, String>(pairs.size / 2)
                    for (index in pairs.indices step 2) result[pairs[index]] = pairs[index + 1]
                    return result
                }
                val languageAliases = aliases("languageAliases")
                val regionAliases = aliases("regionAliases")
                val buffer = required("trie").getBinary()
                val trie = buffer.readBytes(buffer.remaining)
                val m49 = required("m49").getStringArray()
                val lsrs = LSR.decodeInts(required("lsrnum").getIntVector(), m49)
                return Data(languageAliases, regionAliases, trie, lsrs)
            }
        }
    }
    private val languageAliases = data.languageAliases
    private val regionAliases = data.regionAliases
    private val trie = BytesTrie(data.trie, 0)
    private val lsrs = data.lsrs
    private val trieUndState: Long
    private val trieUndZzzzState: Long
    private val defaultLsrIndex: Int
    private val trieFirstLetterStates = LongArray(26)
    init {
        trie.next('*'.code)
        trieUndState = trie.getState64()
        trie.next('*'.code)
        trieUndZzzzState = trie.getState64()
        trie.next('*'.code)
        defaultLsrIndex = trie.getValue()
        trie.reset()
        for (c in 'a'..'z') {
            if (trie.next(c.code) == BytesTrie.Result.NO_VALUE) trieFirstLetterStates[c - 'a'] = trie.getState64()
            trie.reset()
        }
    }
    fun canonicalize(locale: ULocale): ULocale {
        val language = locale.getLanguage()
        val language2 = languageAliases[language]
        val region = locale.getCountry()
        val region2 = regionAliases[region]
        return if (language2 != null || region2 != null) ULocale(language2 ?: language, locale.getScript(), region2 ?: region) else locale
    }
    fun makeMaximizedLsrFrom(locale: ULocale, returnInputIfUnmatch: Boolean): LSR {
        if (locale.getName().startsWith("@x=")) return LSR(locale.toLanguageTag(), "", "", LSR.EXPLICIT_LSR)
        val max = makeMaximizedLsr(locale.getLanguage(), locale.getScript(), locale.getCountry(), locale.getVariant(), returnInputIfUnmatch)
        return if (max.language.isEmpty() && max.script.isEmpty() && max.region.isEmpty())
            LSR(locale.getLanguage(), locale.getScript(), locale.getCountry(), LSR.EXPLICIT_LSR) else max
    }
    internal fun makeMaximizedLsr(language: String, script: String, region: String, variant: String, returnInputIfUnmatch: Boolean): LSR {
        if (!returnInputIfUnmatch) {
            if (region.length == 2 && region[0] == 'X') {
                val prefix = when (region[1]) { 'A' -> "'"; 'B' -> "+"; 'C' -> ","; else -> null }
                if (prefix != null) return LSR(prefix + language, prefix + script, region, LSR.EXPLICIT_LSR)
            }
            if (variant.startsWith("PS")) {
                val prefix = when (variant) { "PSACCENT" -> "'"; "PSBIDI" -> "+"; "PSCRACK" -> ","; else -> null }
                if (prefix != null) {
                    val code = when (variant) { "PSACCENT" -> "XA"; "PSBIDI" -> "XB"; else -> "XC" }
                    val flags = if (region.isEmpty()) LSR.EXPLICIT_LANGUAGE or LSR.EXPLICIT_SCRIPT else LSR.EXPLICIT_LSR
                    return LSR(prefix + language, prefix + script, region.ifEmpty { code }, flags)
                }
            }
        }
        return maximize(languageAliases[language] ?: language, script, regionAliases[region] ?: region, returnInputIfUnmatch)
    }
    private fun isMacroregion(region: String): Boolean = when (Region.getInstance(region).getType()) {
        Region.RegionType.WORLD, Region.RegionType.CONTINENT, Region.RegionType.SUBCONTINENT -> true
        else -> false
    }
    private fun maximize(languageIn: String, scriptIn: String, regionIn: String, returnInputIfUnmatch: Boolean): LSR {
        var language = if (languageIn == "und") "" else languageIn
        var script = if (scriptIn == "Zzzz") "" else scriptIn
        var region = if (regionIn == "ZZ") "" else regionIn
        if (script.isNotEmpty() && region.isNotEmpty() && language.isNotEmpty()) return LSR(language, script, region, LSR.EXPLICIT_LSR)
        var retainLanguage: Boolean
        var retainScript: Boolean
        var retainRegion: Boolean
        val iter = BytesTrie(trie)
        var state: Long
        val c0 = if (language.length >= 2) language[0] - 'a' else -1
        var value = if (c0 in 0..25 && trieFirstLetterStates[c0] != 0L)
            trieNext(iter.resetToState64(trieFirstLetterStates[c0]), language, 1) else trieNext(iter, language, 0)
        val matchLanguage = value >= 0
        var matchScript = false
        if (value >= 0) { retainLanguage = language.isNotEmpty(); state = iter.getState64() }
        else { retainLanguage = true; iter.resetToState64(trieUndState); state = 0 }
        if (value >= 0 && script.isNotEmpty()) matchScript = true
        if (value > 0) {
            if (value == SKIP_SCRIPT) value = 0
            retainScript = script.isNotEmpty()
        } else {
            value = trieNext(iter, script, 0)
            if (value >= 0) { retainScript = script.isNotEmpty(); state = iter.getState64() }
            else {
                retainScript = true
                if (state == 0L) iter.resetToState64(trieUndZzzzState)
                else { iter.resetToState64(state); value = trieNext(iter, "", 0); state = iter.getState64() }
            }
        }
        var matchRegion = false
        if (value > 0) retainRegion = region.isNotEmpty() else {
            retainRegion = false
            value = trieNext(iter, region, 0)
            if (value >= 0) {
                if (region.isNotEmpty() && !isMacroregion(region)) { retainRegion = true; matchRegion = true }
            } else {
                retainRegion = true
                if (state == 0L) value = defaultLsrIndex else {
                    iter.resetToState64(state)
                    value = trieNext(iter, "", 0)
                    if (value < 0) {
                        retainLanguage = language.isNotEmpty(); retainScript = script.isNotEmpty(); retainRegion = region.isNotEmpty()
                        iter.resetToState64(trieUndState)
                        trieNext(iter, "", 0)
                        val emptyState = iter.getState64()
                        value = trieNext(iter, region, 0)
                        if (value < 0) { iter.resetToState64(emptyState); value = trieNext(iter, "", 0) }
                    }
                }
            }
        }
        if (returnInputIfUnmatch && !(matchLanguage || matchScript || (matchRegion && language.isEmpty())))
            return LSR("", "", "", LSR.EXPLICIT_LSR)
        if (language.isEmpty()) language = "und"
        if (!(retainLanguage || retainScript || retainRegion)) return lsrs[value]
        if (!retainLanguage) language = lsrs[value].language
        if (!retainScript) script = lsrs[value].script
        if (!retainRegion) region = lsrs[value].region
        val flags = (if (retainLanguage) 4 else 0) + (if (retainScript) 2 else 0) + (if (retainRegion) 1 else 0)
        return LSR(language, script, region, flags)
    }
    internal fun compareLikely(lsr: LSR, other: LSR, infoIn: Int): Int {
        var info = infoIn
        if (lsr.language != other.language) return -4
        if (lsr.script != other.script) {
            val index = if (info >= 0 && info and 2 == 0) info shr 2 else getLikelyIndex(lsr.language, "").also { info = it shl 2 }
            return if (lsr.script == lsrs[index].script) info or 1 else info and 1.inv()
        }
        if (lsr.region != other.region) {
            val index = if (info >= 0 && info and 2 != 0) info shr 2 else getLikelyIndex(lsr.language, lsr.region).also { info = (it shl 2) or 2 }
            return if (lsr.region == lsrs[index].region) info or 1 else info and 1.inv()
        }
        return info and 1.inv()
    }
    private fun getLikelyIndex(languageIn: String, scriptIn: String): Int {
        val language = if (languageIn == "und") "" else languageIn
        val script = if (scriptIn == "Zzzz") "" else scriptIn
        val iter = BytesTrie(trie)
        var state: Long
        val c0 = if (language.length >= 2) language[0] - 'a' else -1
        var value = if (c0 in 0..25 && trieFirstLetterStates[c0] != 0L)
            trieNext(iter.resetToState64(trieFirstLetterStates[c0]), language, 1) else trieNext(iter, language, 0)
        if (value >= 0) state = iter.getState64() else { iter.resetToState64(trieUndState); state = 0 }
        if (value > 0) { if (value == SKIP_SCRIPT) value = 0 } else {
            value = trieNext(iter, script, 0)
            if (value >= 0) state = iter.getState64() else {
                if (state == 0L) iter.resetToState64(trieUndZzzzState)
                else { iter.resetToState64(state); value = trieNext(iter, "", 0); state = iter.getState64() }
            }
        }
        if (value <= 0) value = trieNext(iter, "", 0)
        return value
    }
    fun minimizeSubtags(language: String, script: String, region: String, favor: ULocale.Minimize?): LSR {
        val max = maximize(language, script, region, true)
        if (max.language.isEmpty() && max.region.isEmpty() && max.script.isEmpty()) return LSR(language, script, region, LSR.EXPLICIT_LSR)
        if (maximize(max.language, "", "", true).isEquivalentTo(max)) return LSR(max.language, "", "", LSR.DONT_CARE_FLAGS)
        if (favor == ULocale.Minimize.FAVOR_REGION) {
            if (maximize(max.language, "", max.region, true).isEquivalentTo(max)) return LSR(max.language, "", max.region, LSR.DONT_CARE_FLAGS)
            if (maximize(max.language, max.script, "", true).isEquivalentTo(max)) return LSR(max.language, max.script, "", LSR.DONT_CARE_FLAGS)
        } else {
            if (maximize(max.language, max.script, "", true).isEquivalentTo(max)) return LSR(max.language, max.script, "", LSR.DONT_CARE_FLAGS)
            if (maximize(max.language, "", max.region, true).isEquivalentTo(max)) return LSR(max.language, "", max.region, LSR.DONT_CARE_FLAGS)
        }
        return LSR(max.language, max.script, max.region, LSR.DONT_CARE_FLAGS)
    }
    private fun getTable(): Map<String, LSR> {
        val map = HashMap<String, LSR>()
        for (entry in trie) {
            val key = buildString {
                for (i in 0 until entry.bytesLength()) {
                    val b = entry.byteAt(i).toInt()
                    when { b == '*'.code -> append("*-"); b >= 0 -> append(b.toChar()); else -> append((b and 0x7f).toChar()).append('-') }
                }
            }.dropLast(1)
            map[key] = lsrs[entry.value]
        }
        return map.entries.sortedBy { it.key }.associate { it.key to it.value }
    }
    override fun toString(): String = getTable().toString()
    companion object {
        const val SKIP_SCRIPT = 1
        val INSTANCE: LikelySubtags = LikelySubtags(Data.load())
        private fun trieNext(iter: BytesTrie, s: String, start: Int): Int {
            val result: BytesTrie.Result
            if (s.isEmpty()) result = iter.next('*'.code) else {
                var index = start
                while (index < s.lastIndex) { if (!iter.next(s[index].code).hasNext()) return -1; index++ }
                result = iter.next(s[index].code or 0x80)
            }
            return when (result) {
                BytesTrie.Result.NO_MATCH -> -1
                BytesTrie.Result.NO_VALUE -> 0
                BytesTrie.Result.INTERMEDIATE_VALUE -> SKIP_SCRIPT
                BytesTrie.Result.FINAL_VALUE -> iter.getValue()
            }
        }
    }
}
