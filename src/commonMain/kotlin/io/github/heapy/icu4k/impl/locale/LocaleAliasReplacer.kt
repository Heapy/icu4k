// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** CLDR alias replacement order and transformed/subdivision keyword canonicalization. */
internal class LocaleAliasReplacer(private var language: String, private var script: String,
    private var region: String?, variants: String, private var extensions: String?) {
    private var variants: MutableList<String>? = if (variants.isEmpty()) null else javaSplit(variants, '_').toMutableList()
    fun replace(): String? {
        val data = aliasData
        var changed = false
        var count = 0
        while (true) {
            if (count++ > 10) throw IllegalArgumentException("Have problem to resolve locale alias of " +
                localeID(language, script, region, variants?.joinToString("_") ?: "") + extensions)
            if (replaceLanguage(data, true, true, true) || replaceLanguage(data, true, true, false) ||
                replaceLanguage(data, true, false, true) || replaceLanguage(data, true, false, false) ||
                replaceLanguage(data, false, false, true) || replaceRegion(data) || replaceScript(data) || replaceVariant(data)) {
                changed = true
                continue
            }
            break
        }
        if (extensions == null && !changed) return null
        var result = localeID(language, script, region, variants?.joinToString("_") ?: "")
        if (extensions != null) {
            var keywordChanged = false
            var temp = ULocale(result + extensions)
            val keywords = temp.getKeywords()
            while (keywords != null && keywords.hasNext()) {
                val key = keywords.next()
                if (key == "rg" || key == "sd" || key == "t") {
                    val value = temp.getKeywordValue(key)!!
                    val replacement = if (key == "t") replaceTransformedExtensions(value) else data.subdivision[value]
                    if (replacement != null) { temp = temp.setKeywordValue(key, replacement); keywordChanged = true }
                }
            }
            if (keywordChanged) { extensions = temp.getName().substring(temp.getBaseName().length); changed = true }
            result += extensions
        }
        return if (changed) result else null
    }
    private fun replaceLanguage(data: AliasData, checkLanguage: Boolean, checkRegion: Boolean, checkVariants: Boolean): Boolean {
        if ((checkRegion && region.isNullOrEmpty()) || (checkVariants && variants == null)) return false
        val size = if (checkVariants) variants!!.size else 1
        val searchLanguage = if (checkLanguage) language else "und"
        val searchRegion = if (checkRegion) region else null
        var searchVariant: String? = null
        for (variantIndex in 0 until size) {
            if (checkVariants) searchVariant = variants!![variantIndex]
            if (searchVariant != null && searchVariant.length < 4) searchVariant = null
            val key = buildString {
                append(searchLanguage)
                if (!searchRegion.isNullOrEmpty()) append('_').append(searchRegion)
                if (!searchVariant.isNullOrEmpty()) append('_').append(searchVariant)
            }
            val replacement = data.language[key] ?: continue
            var replacedScript: String? = null
            var replacedRegion: String? = null
            var replacedVariant: String? = null
            var replacedExtensions: String? = null
            var replacedLanguage: String
            if ('_' !in replacement) replacedLanguage = if (replacement == "und") language else replacement else {
                val fields = javaSplit(replacement, '_')
                replacedLanguage = if (fields[0] == "und") language else fields[0]
                var index = 1
                var consumed = fields[0].length + 1
                while (fields.size > index) {
                    val field = fields[index]
                    val length = field.length
                    when {
                        length == 1 -> { replacedExtensions = replacement.substring(consumed); break }
                        length in 2..3 -> replacedRegion = field
                        length in 5..8 -> replacedVariant = field
                        length == 4 -> if (field[0] in '0'..'9') replacedVariant = field else replacedScript = field
                    }
                    index++
                    consumed += length + 1
                }
            }
            replacedScript = deleteOrReplace(script, null, replacedScript)
            replacedRegion = deleteOrReplace(region, searchRegion, replacedRegion)
            replacedVariant = deleteOrReplace(searchVariant, searchVariant, replacedVariant)
            if (language == replacedLanguage && script == replacedScript && region!!.equals(replacedRegion) &&
                searchVariant == replacedVariant && replacedExtensions == null) continue
            language = replacedLanguage
            script = replacedScript!!
            region = replacedRegion
            if (!searchVariant.isNullOrEmpty()) {
                if (!replacedVariant.isNullOrEmpty()) variants!![variantIndex] = replacedVariant else {
                    variants!!.removeAt(variantIndex)
                    if (variants!!.isEmpty()) variants = null
                }
            }
            // Upstream intentionally ignores extensions embedded in replacement data.
            return true
        }
        return false
    }
    private fun replaceRegion(data: AliasData): Boolean {
        if (region.isNullOrEmpty()) return false
        val replacements = data.territory[region] ?: return false
        region = if (replacements.size > 1) {
            val likely = ULocale.addLikelySubtags(ULocale(language, script, null)).getCountry()
            if (likely in replacements) likely else replacements[0]
        } else replacements[0]
        return true
    }
    private fun replaceScript(data: AliasData): Boolean {
        if (script.isEmpty()) return false
        script = data.script[script] ?: return false
        return true
    }
    private fun replaceVariant(data: AliasData): Boolean {
        val list = variants ?: return false
        for (index in list.indices) {
            val variant = list[index]
            val replacement = data.variant[variant] ?: continue
            if (variant != replacement) {
                list[index] = replacement
                if (variant == "heploc") { list.remove("hepburn"); if (list.isEmpty()) variants = null }
                return true
            }
        }
        return false
    }
    private fun replaceTransformedExtensions(extensions: String): String {
        val builder = StringBuilder()
        val fields = ArrayList<String>()
        var processedLength = 0
        var languageLength = 0
        var key = ""
        for (subtag in javaSplit(extensions, '-')) {
            if (LanguageTag.isTKey(subtag)) {
                if (languageLength == 0) languageLength = processedLength - 1
                if (builder.isNotEmpty()) { fields.add(builder.toString()); builder.setLength(0) }
                key = subtag
                builder.append(subtag)
            } else if (languageLength != 0) builder.append('-').append(ULocale.toUnicodeLocaleType(key, subtag))
            processedLength += subtag.length + 1
        }
        if (builder.isNotEmpty()) { fields.add(builder.toString()); builder.setLength(0) }
        val transformedLanguage = if (languageLength > 0) extensions.substring(0, languageLength) else if (fields.isEmpty()) extensions else ""
        if (transformedLanguage.isNotEmpty()) {
            val canonical = ULocale.createCanonical(ULocale.forLanguageTag(extensions)).toLanguageTag()
            builder.append(AsciiUtil.toLowerString(canonical))
        }
        if (fields.isNotEmpty()) {
            if (builder.isNotEmpty()) builder.append('-')
            fields.sort()
            builder.append(fields.joinToString("-"))
        }
        return builder.toString()
    }
    private class AliasData(val language: Map<String, String>, val script: Map<String, String>,
        val territory: Map<String, List<String>>, val variant: Map<String, String>, val subdivision: Map<String, String>)
    companion object {
        private val aliasData: AliasData by lazy {
            val alias = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metadata")["alias"]
            val languageBundle = alias["language"]
            val scriptBundle = alias["script"]
            val territoryBundle = alias["territory"]
            val variantBundle = alias["variant"]
            val subdivisionBundle = alias["subdivision"]
            fun read(bundle: UResourceBundle, validate: (String, String) -> String): Map<String, String> {
                val map = HashMap<String, String>()
                for (index in 0 until bundle.getSize()) {
                    val resource = bundle[index]
                    val from = resource.getKey()!!
                    val to = resource["replacement"].getString()
                    map[from] = validate(from, to)
                }
                return map
            }
            // Java's single-argument Locale constructor always has empty script and country;
            // the upstream language-key guard therefore does not reject any string key.
            val language = read(languageBundle) { _, to -> to }
            val script = read(scriptBundle) { from, to ->
                if (from.length != 4) throw IllegalArgumentException("Incorrect key [$from] in alias:script.")
                to
            }
            val territory = read(territoryBundle) { from, to ->
                if (from.length !in 2..3) throw IllegalArgumentException("Incorrect key [$from] in alias:territory.")
                to
            }.mapValues { javaSplit(it.value, ' ') }
            fun validVariant(value: String): Boolean = value.length in 4..8 && (value.length != 4 || value[0] in '0'..'9')
            val variant = read(variantBundle) { from, to ->
                if (!validVariant(from)) throw IllegalArgumentException("Incorrect key [$from] in alias:variant.")
                if (!validVariant(to)) throw IllegalArgumentException("Incorrect variant [$to] for the key [$from] in alias:variant.")
                to
            }
            val subdivision = read(subdivisionBundle) { from, raw ->
                var to = javaSplit(raw, ' ')[0]
                if (from.length !in 3..8) throw IllegalArgumentException("Incorrect key [$from] in alias:territory.")
                if (to.length == 2) to += "zzzz" else if (to.length !in 2..8) throw IllegalArgumentException("Incorrect value [$to] in alias:territory.")
                to
            }
            AliasData(language, script, territory, variant, subdivision)
        }
        private fun deleteOrReplace(input: String?, type: String?, replacement: String?): String? =
            if (!replacement.isNullOrEmpty()) if (input.isNullOrEmpty()) replacement else input else if (type.isNullOrEmpty()) input else null
        private fun javaSplit(value: String, separator: Char): List<String> =
            if (value.isEmpty()) listOf("") else value.split(separator).dropLastWhile { it.isEmpty() }
        private fun localeID(language: String?, script: String?, region: String?, variant: String?): String = buildString {
            if (!language.isNullOrEmpty()) append(language)
            if (!script.isNullOrEmpty()) append('_').append(script)
            if (!region.isNullOrEmpty()) append('_').append(region)
            if (!variant.isNullOrEmpty()) { if (region.isNullOrEmpty()) append('_'); append('_').append(variant) }
        }
    }
}
