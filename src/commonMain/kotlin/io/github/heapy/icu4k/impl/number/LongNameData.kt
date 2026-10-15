// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.CurrencyData
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.*

/** Resource and grammatical-rule portion of ICU4J LongNameHandler. */
internal object LongNameData {
    val DNAM_INDEX = StandardPlural.COUNT
    val PER_INDEX = StandardPlural.COUNT + 1
    val GENDER_INDEX = StandardPlural.COUNT + 2
    val CONSTANT_DENOMINATOR_INDEX = StandardPlural.COUNT + 3
    val ARRAY_LENGTH = StandardPlural.COUNT + 4

    fun getIndex(keyword: String?): Int = when (keyword!!) {
        "dnam" -> DNAM_INDEX
        "per" -> PER_INDEX
        "gender" -> GENDER_INDEX
        else -> StandardPlural.fromString(keyword).ordinal
    }

    fun getWithPlural(strings: Array<String?>?, plural: StandardPlural?): String =
        strings!![plural!!.ordinal] ?: strings[StandardPlural.OTHER.ordinal]
        ?: throw ICUException("Could not find data in 'other' plural variant")

    enum class PlaceholderPosition { NONE, BEGINNING, MIDDLE, END }
    class ExtractCorePatternResult(val coreUnit: String, val placeholderPosition: PlaceholderPosition, val joinerChar: Char)

    fun extractCorePattern(pattern: String?): ExtractCorePatternResult {
        val length = pattern!!.length
        if (pattern.startsWith("{0}")) {
            return if (length > 3 && UCharacter.isSpaceChar(pattern[3].code))
                ExtractCorePatternResult(pattern.substring(4), PlaceholderPosition.BEGINNING, pattern[3])
            else ExtractCorePatternResult(pattern.substring(3), PlaceholderPosition.BEGINNING, '\u0000')
        }
        if (pattern.endsWith("{0}")) {
            return if (UCharacter.isSpaceChar(pattern[length - 4].code))
                ExtractCorePatternResult(pattern.substring(0, length - 4), PlaceholderPosition.END, pattern[length - 4])
            else ExtractCorePatternResult(pattern.substring(0, length - 3), PlaceholderPosition.END, '\u0000')
        }
        return ExtractCorePatternResult(pattern, if (pattern.indexOf("{0}", 1) == -1)
            PlaceholderPosition.NONE else PlaceholderPosition.MIDDLE, '\u0000')
    }

    private fun units(locale: ULocale?): ICUResourceBundle =
        UResourceBundle.getBundleInstance(ICUData.ICU_UNIT_BASE_NAME, locale) as ICUResourceBundle
    private fun widthKey(width: UnitWidth?): String = "units" + when (width) {
        UnitWidth.NARROW -> "Narrow"
        UnitWidth.SHORT -> "Short"
        else -> ""
    }

    fun getGenderForBuiltin(locale: ULocale?, unit: MeasureUnit?): String {
        val bundle = units(locale)
        val key = StringBuilder("units/").append(unit!!.getType()).append('/')
        // Preserve the source's repeated virtual getter calls.
        if (unit.getSubtype() != null && unit.getSubtype()!!.endsWith("-person"))
            key.append(unit.getSubtype()!!, 0, unit.getSubtype()!!.length - 7)
        else key.append(unit.getSubtype())
        key.append("/gender")
        return try { bundle.getWithFallback(key.toString()).getString() }
        catch (_: MissingResourceException) { "" }
    }

    class InflectedPluralSink(private val gender: String?, private val caseVariant: String?, private val out: Array<String?>?) : UResource.Sink() {
        init { for (i in 0 until ARRAY_LENGTH) out!![i] = null }
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val plurals = value.getTable()
            var i = 0
            while (plurals.getKeyAndValue(i++, key, value)) {
                val index = getIndex(key.toString())
                if (out!![index] != null) continue
                val genders = value.getTable()
                if (loadPlural(genders, value)) out[index] = value.getString()
            }
        }
        private fun loadPlural(table: UResource.Table, value: UResource.Value): Boolean {
            if (!gender.isNullOrEmpty()) {
                if (loadGender(table, gender, value)) return true
                if (gender !== "neuter" && loadGender(table, "neuter", value)) return true
            }
            return loadGender(table, "_", value)
        }
        private fun loadGender(table: UResource.Table, gender: String, value: UResource.Value): Boolean {
            if (!table.findValue(gender, value)) return false
            val cases = value.getTable()
            if (!caseVariant.isNullOrEmpty()) {
                if (cases.findValue(caseVariant, value)) return true
                if (caseVariant !== "nominative" && cases.findValue("nominative", value)) return true
            }
            return cases.findValue("_", value)
        }
    }

    fun getInflectedMeasureData(subKey: String?, locale: ULocale?, width: UnitWidth?, gender: String?, caseVariant: String?, out: Array<String?>?) {
        val sink = InflectedPluralSink(gender, caseVariant, out)
        val bundle = units(locale)
        val key = widthKey(width) + "/" + subKey
        try {
            bundle.getAllItemsWithFallback(key, sink)
            if (width == UnitWidth.SHORT) return
        } catch (_: MissingResourceException) { }
        // ICU4J repeats the same key here, despite its comment about falling back to short.
        bundle.getAllItemsWithFallback(key, sink)
    }

    class PluralTableSink(private val out: Array<String?>?) : UResource.Sink() {
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                val keyword = key.toString()
                if (keyword == "case") continue
                val index = getIndex(keyword)
                if (out!![index] != null) continue
                out[index] = value.getString()
            }
        }
    }
    private class AliasSink : UResource.Sink() {
        var replacement: String? = null
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                if (key.toString() == "replacement") replacement = value.toString()
            }
        }
    }

    fun getMeasureData(locale: ULocale?, unit: MeasureUnit?, width: UnitWidth?, unitDisplayCase: String?, out: Array<String?>?) {
        val sink = PluralTableSink(out)
        val bundle = units(locale)
        val subKey = StringBuilder("/").append(unit!!.getType()).append('/')
        var subtype = unit.getSubtype()
        val metadata = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metadata") as ICUResourceBundle
        val alias = AliasSink()
        metadata.getAllItemsWithFallbackNoFail("alias/unit/" + subtype, alias)
        if (alias.replacement != null) subtype = alias.replacement
        if (subtype != null && subtype.endsWith("-person")) subKey.append(subtype, 0, subtype.length - 7)
        else subKey.append(subtype)
        if (width != UnitWidth.FULL_NAME) {
            try {
                val gender = bundle.getWithFallback("units$subKey/gender").getString()
                out!![GENDER_INDEX] = gender
            } catch (_: MissingResourceException) { }
        }
        val key = widthKey(width) + subKey
        if (width == UnitWidth.FULL_NAME && !unitDisplayCase.isNullOrEmpty()) {
            try { bundle.getAllItemsWithFallback("$key/case/$unitDisplayCase", sink) }
            catch (_: MissingResourceException) { }
        }
        try { bundle.getAllItemsWithFallback(key, sink) }
        catch (e: MissingResourceException) { throw IllegalArgumentException("No data for unit $unit, width $width", e) }
    }

    fun getCurrencyLongNameData(locale: ULocale?, currency: Currency?, out: Array<String?>?) {
        val data = CurrencyData.provider.getInstance(locale, true)!!.getUnitPatterns()
        val keywords = PluralRules.forLocale(locale).getKeywords()
        val other = data!!["other"]
        if (keywords != null && other != null) {
            for (keyword in keywords) {
                if (keyword != "other" && !data.containsKey(keyword)) (data as MutableMap<String, String>)[keyword] = other
            }
        }
        for (entry in data.entries) {
            val keyword = entry.key
            val index = getIndex(keyword)
            val name = currency!!.getName(locale, Currency.PLURAL_LONG_NAME, keyword, null)
            val pattern = entry.value
            val simple = pattern.replace("{1}", name!!)
            out!![index] = simple
        }
    }

    fun getCompoundValue(compoundKey: String?, locale: ULocale?, width: UnitWidth?): String {
        val bundle = units(locale)
        val key = widthKey(width) + "/compound/" + compoundKey
        try { return bundle.getStringWithFallback(key) }
        catch (_: MissingResourceException) { if (width == UnitWidth.SHORT) return "" }
        try { return bundle.getStringWithFallback(key) }
        catch (_: MissingResourceException) { return "" }
    }

    private fun derivations(locale: ULocale?): ICUResourceBundle {
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "grammaticalFeatures")
            .get("grammaticalData").get("derivations") as ICUResourceBundle
        return try { bundle.get(locale!!.getLanguage()) as ICUResourceBundle }
        catch (_: MissingResourceException) { bundle.get("root") as ICUResourceBundle }
    }

    class DerivedComponents(locale: ULocale?, feature: String?, structure: String?) {
        private var first: String? = ""
        private var second: String? = ""
        init {
            try {
                val bundle = derivations(locale).get("component").get(feature!!).get(structure!!)
                val a = bundle.getString(0)
                first = if (a == "compound") null else a
                val b = bundle.getString(1)
                second = if (b == "compound") null else b
            } catch (_: MissingResourceException) { }
        }
        fun value0(compoundValue: String?): String? = first ?: compoundValue
        fun value1(compoundValue: String?): String? = second ?: compoundValue
    }

    fun getDeriveCompoundRule(locale: ULocale?, feature: String?, structure: String?): String =
        derivations(locale).get("compound").get(feature!!).getString(structure!!)

    fun getDerivedGender(locale: ULocale?, structure: String?, data0: Array<String?>?, data1: Array<String?>?): String? {
        val rule = getDeriveCompoundRule(locale, "gender", structure)
        if (rule.length == 1) when (rule[0]) {
            '0' -> return data0!![GENDER_INDEX]
            '1' -> return data1?.get(GENDER_INDEX)
        }
        return rule
    }
}
