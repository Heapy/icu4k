// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.CurrencyData.*
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.atomics.AtomicReference

open class ICUCurrencyDisplayInfoProvider : CurrencyDisplayInfoProvider {
    private val cache = AtomicReference<DisplayInfo?>(null)
    override fun hasData(): Boolean = true
    override fun getInstance(locale: ULocale?, withFallback: Boolean): CurrencyDisplayInfo? {
        val actual = locale ?: ULocale.ROOT
        val old = cache.load()
        if (old != null && old.locale == actual && old.fallback == withFallback) return old
        val bundle = if (withFallback) ICUResourceBundle.getBundleInstance(ICUData.ICU_CURR_BASE_NAME, actual.getBaseName(), defaultICUResourceLoader(), ICUResourceBundle.OpenType.LOCALE_DEFAULT_ROOT)
        else try { ICUResourceBundle.getBundleInstance(ICUData.ICU_CURR_BASE_NAME, actual.getBaseName(), defaultICUResourceLoader(), ICUResourceBundle.OpenType.LOCALE_ONLY) } catch (_: MissingResourceException) { return null }
        return DisplayInfo(actual, bundle, withFallback).also { cache.store(it) }
    }
    internal class DisplayInfo(val locale: ULocale, private val bundle: ICUResourceBundle, val fallback: Boolean) : CurrencyDisplayInfo() {
        private class Formatting(val isoCode: String?) { var name: String? = null; var symbol: String? = null; var format: CurrencyFormatInfo? = null }
        private class Variant(val isoCode: String?, val variant: String) { var symbol: String? = null }
        private class Parsing { val symbols = HashMap<String, String>(); val names = HashMap<String, String>() }
        private val formatting = AtomicReference<Formatting?>(null)
        private val variant = AtomicReference<Variant?>(null)
        private val plurals = AtomicReference<Array<String?>?>(null)
        private val parsing = AtomicReference<CacheReference<Parsing>?>(null)
        private val units = AtomicReference<MutableMap<String, String>?>(null)
        private val spacing = AtomicReference<CurrencySpacingInfo?>(null)
        override fun getULocale(): ULocale = bundle.getULocale()
        override fun getName(isoCode: String?): String? = fetchFormatting(isoCode).name.let { if (it == null && fallback) isoCode else it }
        override fun getSymbol(isoCode: String?): String? = fetchFormatting(isoCode).symbol.let { if (it == null && fallback) isoCode else it }
        private fun symbol(isoCode: String?, kind: String): String? = fetchVariant(isoCode, kind).symbol.let { if (it == null && fallback) getSymbol(isoCode) else it }
        override fun getNarrowSymbol(isoCode: String?): String? = symbol(isoCode, "narrow")
        override fun getFormalSymbol(isoCode: String?): String? = symbol(isoCode, "formal")
        override fun getVariantSymbol(isoCode: String?): String? = symbol(isoCode, "variant")
        override fun getPluralName(isoCode: String?, pluralKey: String?): String? {
            val plural = StandardPlural.orNullFromString(pluralKey)
            val data = fetchPlurals(isoCode)
            val keys = fetchUnits().keys
            var result = plural?.let { data[1 + it.ordinal] }
            if (result == null && (fallback || pluralKey in keys)) result = data[1 + StandardPlural.OTHER_INDEX]
            if (result == null && (fallback || pluralKey in keys)) result = fetchFormatting(isoCode).name
            if (result == null && fallback) result = isoCode
            return result
        }
        override fun symbolMap(): Map<String, String> = fetchParsing().symbols
        override fun nameMap(): Map<String, String> = fetchParsing().names
        override fun getUnitPatterns(): Map<String, String> = fetchUnits()
        override fun getFormatInfo(isoCode: String?): CurrencyFormatInfo? = fetchFormatting(isoCode).format
        override fun getSpacingInfo(): CurrencySpacingInfo {
            val data = fetchSpacing()
            return if ((!data.hasBeforeCurrency || !data.hasAfterCurrency) && fallback) CurrencySpacingInfo.DEFAULT else data
        }
        private fun fetchFormatting(code: String?): Formatting {
            formatting.load()?.let { if (it.isoCode!!.equals(code)) return it }
            val result = Formatting(code)
            val sink = Sink(!fallback, Entry.CURRENCIES); sink.formatting = result
            bundle.getAllItemsWithFallbackNoFail("Currencies/$code", sink)
            formatting.store(result); return result
        }
        private fun fetchVariant(code: String?, kind: String): Variant {
            variant.load()?.let { if (it.isoCode!!.equals(code) && it.variant == kind) return it }
            val result = Variant(code, kind)
            val sink = Sink(!fallback, Entry.VARIANT); sink.variant = result
            bundle.getAllItemsWithFallbackNoFail("Currencies%$kind/$code", sink)
            variant.store(result); return result
        }
        private fun fetchPlurals(code: String?): Array<String?> {
            plurals.load()?.let { if (it[0]!!.equals(code)) return it }
            val result = arrayOfNulls<String>(1 + StandardPlural.COUNT); result[0] = code
            val sink = Sink(!fallback, Entry.PLURALS); sink.plurals = result
            bundle.getAllItemsWithFallbackNoFail("CurrencyPlurals/$code", sink)
            plurals.store(result); return result
        }
        private fun fetchParsing(): Parsing {
            parsing.load()?.get()?.let { return it }
            val result = Parsing(); val sink = Sink(!fallback, Entry.TOP); sink.parsing = result
            bundle.getAllItemsWithFallback("", sink)
            parsing.store(createSimpleCacheReference(result, false)); return result
        }
        private fun fetchUnits(): MutableMap<String, String> {
            units.load()?.let { return it }
            val result = HashMap<String, String>(); val sink = Sink(false, Entry.UNITS); sink.units = result
            bundle.getAllItemsWithFallback("CurrencyUnitPatterns", sink)
            units.store(result); return result
        }
        private fun fetchSpacing(): CurrencySpacingInfo {
            spacing.load()?.let { return it }
            val result = CurrencySpacingInfo(); val sink = Sink(!fallback, Entry.SPACING); sink.spacing = result
            bundle.getAllItemsWithFallback("currencySpacing", sink)
            spacing.store(result); return result
        }
        private enum class Entry { TOP, CURRENCIES, PLURALS, VARIANT, SPACING, UNITS }
        private class Sink(val noRoot: Boolean, val entry: Entry) : UResource.Sink() {
            var formatting: Formatting? = null; var variant: Variant? = null; var plurals: Array<String?>? = null
            var parsing: Parsing? = null; var units: MutableMap<String, String>? = null; var spacing: CurrencySpacingInfo? = null
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                if (noRoot && noFallback) return
                when (entry) {
                    Entry.TOP -> top(key, value)
                    Entry.CURRENCIES -> currency(key, value)
                    Entry.PLURALS -> plural(key, value)
                    Entry.VARIANT -> if (variant!!.symbol == null) variant!!.symbol = value.getString()
                    Entry.SPACING -> spacing(key, value)
                    Entry.UNITS -> table(key, value) { k, v -> if (units!![k.toString()] == null) units!![k.toString()] = v.getString() }
                }
            }
            private fun table(key: UResource.Key, value: UResource.Value, consume: (UResource.Key, UResource.Value) -> Unit) {
                val table = value.getTable(); var i = 0
                while (table.getKeyAndValue(i++, key, value)) consume(key, value)
            }
            private fun top(key: UResource.Key, value: UResource.Value) = table(key, value) { k, v -> when (k.toString()) {
                "Currencies" -> table(k, v) { codeKey, data ->
                    val code = codeKey.toString(); checkArray(code, data); val array = data.getArray()
                    parsing!!.symbols[code] = code
                    array.getValue(0, data); parsing!!.symbols[data.getString()] = code
                    array.getValue(1, data); parsing!!.names[data.getString()] = code
                }
                "Currencies%variant" -> table(k, v) { codeKey, data -> parsing!!.symbols[data.getString()] = codeKey.toString() }
                "CurrencyPlurals" -> table(k, v) { codeKey, data ->
                    val code = codeKey.toString()
                    table(codeKey, data) { pluralKey, name -> validatePlural(pluralKey); parsing!!.names[name.getString()] = code }
                }
            } }
            private fun checkArray(code: String, value: UResource.Value) {
                if (value.getType() != UResourceBundle.ARRAY) throw ICUException("Unexpected data type in Currencies table for $code")
            }
            private fun currency(key: UResource.Key, value: UResource.Value) {
                val code = key.toString(); checkArray(code, value); val array = value.getArray(); val data = formatting!!
                if (data.symbol == null) { array.getValue(0, value); data.symbol = value.getString() }
                if (data.name == null) { array.getValue(1, value); data.name = value.getString() }
                if (array.getSize() > 2 && data.format == null) {
                    array.getValue(2, value); val format = value.getArray()
                    format.getValue(0, value); val pattern = value.getString()
                    format.getValue(1, value); val decimal = value.getString()
                    format.getValue(2, value); val grouping = value.getString()
                    data.format = CurrencyFormatInfo(code, pattern, decimal, grouping)
                }
            }
            private fun validatePlural(key: UResource.Key): StandardPlural = StandardPlural.orNullFromString(key.toString()) ?: throw ICUException("Could not make StandardPlural from keyword $key")
            private fun plural(key: UResource.Key, value: UResource.Value) = table(key, value) { k, v ->
                val index = 1 + validatePlural(k).ordinal
                if (plurals!![index] == null) plurals!![index] = v.getString()
            }
            private fun spacing(key: UResource.Key, value: UResource.Value) = table(key, value) { k, v ->
                val type = when (k.toString()) {
                    "beforeCurrency" -> CurrencySpacingInfo.SpacingType.BEFORE.also { spacing!!.hasBeforeCurrency = true }
                    "afterCurrency" -> CurrencySpacingInfo.SpacingType.AFTER.also { spacing!!.hasAfterCurrency = true }
                    else -> null
                }
                if (type != null) table(k, v) { patternKey, patternValue ->
                    val pattern = when (patternKey.toString()) {
                        "currencyMatch" -> CurrencySpacingInfo.SpacingPattern.CURRENCY_MATCH
                        "surroundingMatch" -> CurrencySpacingInfo.SpacingPattern.SURROUNDING_MATCH
                        "insertBetween" -> CurrencySpacingInfo.SpacingPattern.INSERT_BETWEEN
                        else -> null
                    }
                    if (pattern != null) spacing!!.setSymbolIfNull(type, pattern, patternValue.getString())
                }
            }
        }
    }
}
