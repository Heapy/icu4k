// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.AsciiUtil
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.BreakIterator
import io.github.heapy.icu4k.text.CaseMap
import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Component lookup/context rules extracted from LocaleDisplayNamesImpl. */
internal class LocaleDisplayNameComponents(requested: ULocale?, vararg contexts: DisplayContext?) {
    enum class Usage { LANGUAGE, SCRIPT, TERRITORY, VARIANT, KEY, KEYVALUE }
    var dialectNames = false; private set
    private var capitalization = DisplayContext.CAPITALIZATION_NONE
    private var nameLength = DisplayContext.LENGTH_FULL
    private var substitute = DisplayContext.SUBSTITUTE
    val langData: LocaleDisplayNameDataTable
    private val regionData: LocaleDisplayNameDataTable
    val locale: ULocale
    val separatorFormat: String
    val format: String
    val keyTypeFormat: String
    val openParen: Char
    val closeParen: Char
    val replaceOpenParen: Char
    val replaceCloseParen: Char
    private var capitalizationUsage: BooleanArray? = null
    private var capitalizationIterator: BreakIterator? = null
    private val lock = createTextTrieMapLock(this)
    private val currencyInfo: CurrencyData.CurrencyDisplayInfo?
    init {
        for (nullable in contexts) {
            val context = nullable!!
            when (context.type()) {
                DisplayContext.Type.DIALECT_HANDLING -> dialectNames = context.value() != DisplayContext.STANDARD_NAMES.value()
                DisplayContext.Type.CAPITALIZATION -> capitalization = context
                DisplayContext.Type.DISPLAY_LENGTH -> nameLength = context
                DisplayContext.Type.SUBSTITUTE_HANDLING -> substitute = context
            }
        }
        val noSubstitute = substitute == DisplayContext.NO_SUBSTITUTE
        langData = ResourceLocaleDisplayNameDataTable(ICUData.ICU_LANG_BASE_NAME, requested, noSubstitute)
        regionData = ResourceLocaleDisplayNameDataTable(ICUData.ICU_REGION_BASE_NAME, requested, noSubstitute)
        locale = if (langData.getLocale() == ULocale.ROOT) regionData.getLocale() else langData.getLocale()
        val buffer = StringBuilder()
        val separator = langData.get("localeDisplayPattern", "separator").let { if (it == null || it == "separator") "{0}, {1}" else it }
        separatorFormat = SimpleFormatterImpl.compileToStringMinMaxArguments(separator, buffer, 2, 2)
        val pattern = langData.get("localeDisplayPattern", "pattern").let { if (it == null || it == "pattern") "{0} ({1})" else it }
        format = SimpleFormatterImpl.compileToStringMinMaxArguments(pattern, buffer, 2, 2)
        if ('（' in pattern) { openParen = '（'; closeParen = '）'; replaceOpenParen = '［'; replaceCloseParen = '］' }
        else { openParen = '('; closeParen = ')'; replaceOpenParen = '['; replaceCloseParen = ']' }
        val keyPattern = langData.get("localeDisplayPattern", "keyTypePattern").let { if (it == null || it == "keyTypePattern") "{0}={1}" else it }
        keyTypeFormat = SimpleFormatterImpl.compileToStringMinMaxArguments(keyPattern, buffer, 2, 2)
        var needIterator = false
        if (capitalization == DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU || capitalization == DisplayContext.CAPITALIZATION_FOR_STANDALONE) {
            val flags = BooleanArray(Usage.entries.size); capitalizationUsage = flags
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, requested) as ICUResourceBundle
            try {
                bundle.getAllItemsWithFallback("contextTransforms", object : UResource.Sink() {
                    override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                        val table = value.getTable(); var index = 0
                        while (table.getKeyAndValue(index++, key, value)) {
                            val usage = when (key.toString()) {
                                "languages" -> Usage.LANGUAGE; "script" -> Usage.SCRIPT; "territory" -> Usage.TERRITORY
                                "variant" -> Usage.VARIANT; "key" -> Usage.KEY; "keyValue" -> Usage.KEYVALUE
                                else -> continue
                            }
                            val vector = value.getIntVector()
                            if (vector.size < 2) continue
                            val titlecase = vector[if (capitalization == DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU) 0 else 1]
                            if (titlecase != 0) { flags[usage.ordinal] = true; needIterator = true }
                        }
                    }
                })
            } catch (_: MissingResourceException) { }
        }
        if (needIterator || capitalization == DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE) capitalizationIterator = BreakIterator.getSentenceInstance(requested)
        currencyInfo = CurrencyData.provider.getInstance(requested, false)
    }
    fun getContext(type: DisplayContext.Type?): DisplayContext = when (type!!) {
        DisplayContext.Type.DIALECT_HANDLING -> if (dialectNames) DisplayContext.DIALECT_NAMES else DisplayContext.STANDARD_NAMES
        DisplayContext.Type.CAPITALIZATION -> capitalization
        DisplayContext.Type.DISPLAY_LENGTH -> nameLength
        DisplayContext.Type.SUBSTITUTE_HANDLING -> substitute
    }
    fun adjust(usage: Usage, name: String?): String? {
        if (name != null && name.isNotEmpty() && UCharacter.isLowerCase(UTF16.charAt(name, 0)) &&
            (capitalization == DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE || capitalizationUsage?.get(usage.ordinal) == true)) {
            return lock.withLock {
                val iterator = capitalizationIterator ?: BreakIterator.getSentenceInstance(locale).also { capitalizationIterator = it }
                CaseMap.toTitle().noLowercase().noBreakAdjustment().apply(locale, iterator, name)
            }
        }
        return name
    }
    fun localeDisplayName(localeId: String?): String? = localeDisplayName(ULocale(localeId!!))
    fun localeDisplayName(input: ULocale?): String? {
        val target = input!!
        val language = target.getLanguage().ifEmpty { "und" }
        val script = target.getScript()
        val country = target.getCountry()
        val variant = target.getVariant()
        var hasScript = script.isNotEmpty()
        var hasCountry = country.isNotEmpty()
        var resultName: String? = null
        if (dialectNames) {
            if (hasScript && hasCountry) {
                val id = "${language}_${script}_$country"
                val result = localeIdName(id)
                if (result != null && result != id) { resultName = result; hasScript = false; hasCountry = false }
            }
            if (resultName == null && hasScript) {
                val id = "${language}_$script"
                val result = localeIdName(id)
                if (result != null && result != id) { resultName = result; hasScript = false }
            }
            if (resultName == null && hasCountry) {
                val id = "${language}_$country"
                val result = localeIdName(id)
                if (result != null && result != id) { resultName = result; hasCountry = false }
            }
        }
        if (resultName == null) resultName = escapeParens(localeIdName(language) ?: return null)
        val remainder = StringBuilder()
        if (hasScript) remainder.append(escapeParens(scriptDisplayNameInContext(script, true) ?: return null))
        if (hasCountry) appendWithSep(escapeParens(regionDisplayName(country, true) ?: return null), remainder)
        if (variant.isNotEmpty()) appendWithSep(escapeParens(variantDisplayName(variant, true) ?: return null), remainder)
        val keys = target.getKeywords()
        if (keys != null) while (keys.hasNext()) {
            val key = keys.next()
            val value = target.getKeywordValue(key)
            val keyName = escapeParens(keyDisplayName(key, true) ?: return null)
            val valueName = escapeParens(keyValueDisplayName(key, value, true) ?: return null)
            if (valueName != value) appendWithSep(valueName, remainder)
            else if (key != keyName) appendWithSep(SimpleFormatterImpl.formatCompiledPattern(keyTypeFormat, keyName, valueName), remainder)
            else appendWithSep(keyName, remainder).append('=').append(valueName)
        }
        if (remainder.isNotEmpty()) resultName = SimpleFormatterImpl.formatCompiledPattern(format, resultName, remainder.toString())
        return adjust(Usage.LANGUAGE, resultName)
    }
    private fun escapeParens(value: String): String = value.replace(openParen, replaceOpenParen).replace(closeParen, replaceCloseParen)
    private fun appendWithSep(value: String, buffer: StringBuilder): StringBuilder {
        if (buffer.isEmpty()) buffer.append(value)
        else SimpleFormatterImpl.formatAndReplace(separatorFormat, buffer, null, buffer, value)
        return buffer
    }
    fun localeIdName(id: String?): String? {
        if (nameLength == DisplayContext.LENGTH_SHORT) langData.get("Languages%short", id)?.let { if (it != id) return it }
        var name = langData.get("Languages", id)
        if ((name == null || name == id) && '_' !in id!!) {
            val canonical = ULocale.createCanonical(id).getName()
            if (nameLength == DisplayContext.LENGTH_SHORT) langData.get("Languages%short", canonical)?.let { if (it != canonical) return it }
            name = langData.get("Languages", canonical)
        }
        return name
    }
    fun languageDisplayName(lang: String?): String? {
        if (lang == "root" || '_' in lang!!) return if (substitute == DisplayContext.SUBSTITUTE) lang else null
        if (nameLength == DisplayContext.LENGTH_SHORT) langData.get("Languages%short", lang)?.let { if (it != lang) return adjust(Usage.LANGUAGE, it) }
        var name = langData.get("Languages", lang)
        if (name == null || name == lang) {
            val canonical = ULocale.createCanonical(lang).getName()
            if (nameLength == DisplayContext.LENGTH_SHORT) langData.get("Languages%short", canonical)?.let { if (it != canonical) return adjust(Usage.LANGUAGE, it) }
            name = langData.get("Languages", canonical)
        }
        return adjust(Usage.LANGUAGE, name)
    }
    fun scriptDisplayName(script: String?): String? {
        var name = langData.get("Scripts%stand-alone", script)
        if (name == null || name == script) {
            if (nameLength == DisplayContext.LENGTH_SHORT) langData.get("Scripts%short", script)?.let { if (it != script) return adjust(Usage.SCRIPT, it) }
            name = langData.get("Scripts", script)
        }
        return adjust(Usage.SCRIPT, name)
    }
    fun scriptDisplayName(code: Int): String? = scriptDisplayName(UScript.getShortName(code))
    fun scriptDisplayNameInContext(script: String?, skipAdjust: Boolean = false): String? {
        if (nameLength == DisplayContext.LENGTH_SHORT) langData.get("Scripts%short", script)?.let { if (it != script) return if (skipAdjust) it else adjust(Usage.SCRIPT, it) }
        return langData.get("Scripts", script).let { if (skipAdjust) it else adjust(Usage.SCRIPT, it) }
    }
    fun regionDisplayName(region: String?, skipAdjust: Boolean = false): String? {
        if (nameLength == DisplayContext.LENGTH_SHORT) regionData.get("Countries%short", region)?.let { if (it != region) return if (skipAdjust) it else adjust(Usage.TERRITORY, it) }
        return regionData.get("Countries", region).let { if (skipAdjust) it else adjust(Usage.TERRITORY, it) }
    }
    fun variantDisplayName(variant: String?, skipAdjust: Boolean = false): String? = langData.get("Variants", variant).let { if (skipAdjust) it else adjust(Usage.VARIANT, it) }
    fun keyDisplayName(key: String?, skipAdjust: Boolean = false): String? = langData.get("Keys", key).let { if (skipAdjust) it else adjust(Usage.KEY, it) }
    fun keyValueDisplayName(key: String?, value: String?, skipAdjust: Boolean = false): String? {
        var name: String? = null
        if (key!! == "currency") name = currencyInfo!!.getName(AsciiUtil.toUpperString(value!!)) ?: value
        else {
            if (nameLength == DisplayContext.LENGTH_SHORT) langData.get("Types%short", key, value)?.let { if (it != value) name = it }
            if (name == null) name = langData.get("Types", key, value)
        }
        return if (skipAdjust) name else adjust(Usage.KEYVALUE, name)
    }
}
