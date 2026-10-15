// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.impl.SimpleFormatterImpl
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.number.LongNameData.ARRAY_LENGTH
import io.github.heapy.icu4k.impl.number.LongNameData.DNAM_INDEX
import io.github.heapy.icu4k.impl.number.LongNameData.PER_INDEX
import io.github.heapy.icu4k.impl.number.LongNameData.GENDER_INDEX
import io.github.heapy.icu4k.impl.number.LongNameData.CONSTANT_DENOMINATOR_INDEX
import io.github.heapy.icu4k.impl.number.LongNameData.DerivedComponents
import io.github.heapy.icu4k.impl.number.LongNameData.PlaceholderPosition
import io.github.heapy.icu4k.impl.number.LongNameData.getWithPlural
import io.github.heapy.icu4k.impl.number.LongNameData.getMeasureData
import io.github.heapy.icu4k.impl.number.LongNameData.getGenderForBuiltin
import io.github.heapy.icu4k.impl.number.LongNameData.getInflectedMeasureData
import io.github.heapy.icu4k.impl.number.LongNameData.getCompoundValue
import io.github.heapy.icu4k.impl.number.LongNameData.getDerivedGender
import io.github.heapy.icu4k.impl.number.LongNameData.getDeriveCompoundRule
import io.github.heapy.icu4k.impl.units.MeasureUnitImpl
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.text.CaseMap
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.FormatField
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.*
import kotlin.jvm.JvmStatic
import kotlin.math.abs

/** Currency and arbitrary compound measurement-unit names in the number pipeline. */
open class LongNameHandler private constructor(
    private val modifiers: MutableMap<StandardPlural, SimpleModifier>,
    private val rules: PluralRules?, private val parent: MicroPropsGenerator?
) : MicroPropsGenerator, ModifierStore {
    private var gender: String? = ""

    override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
        val micros = parent!!.processQuantity(quantity)
        val plural = RoundingUtils.getPluralSafe(micros!!.rounder, rules, quantity)
        micros.modOuter = modifiers[plural]
        micros.gender = gender
        return micros
    }

    open fun processQuantityWithMicros(quantity: DecimalQuantity?, micros: MicroProps?): MicroProps {
        val plural = RoundingUtils.getPluralSafe(micros!!.rounder, rules, quantity)
        micros.modOuter = modifiers[plural]
        return micros
    }

    override fun getModifier(signum: Modifier.Signum?, plural: StandardPlural?): Modifier? = modifiers[plural]

    private fun simpleFormatsToModifiers(formats: Array<String?>, field: FormatField?) {
        val sb = StringBuilder()
        for (plural in StandardPlural.VALUES) {
            val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(getWithPlural(formats, plural), sb, 0, 1)
            val params = Modifier.Parameters().also { it.obj = this; it.signum = null; it.plural = plural }
            modifiers[plural] = SimpleModifier(compiled, field, false, params)
        }
    }
    private fun multiSimpleFormatsToModifiers(lead: Array<String?>, trail: String, field: FormatField?) {
        val sb = StringBuilder()
        val compiledTrail = SimpleFormatterImpl.compileToStringMinMaxArguments(trail, sb, 1, 1)
        for (plural in StandardPlural.VALUES) {
            val format = getWithPlural(lead, plural)
            val compound = if (format.isEmpty()) trail else SimpleFormatterImpl.formatCompiledPattern(compiledTrail, format)
            val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(compound, sb, 0, 1)
            val params = Modifier.Parameters().also { it.obj = this; it.signum = null; it.plural = plural }
            modifiers[plural] = SimpleModifier(compiled, field, false, params)
        }
    }

    companion object {
        @JvmStatic fun getUnitDisplayName(locale: ULocale?, unit: MeasureUnit?, width: UnitWidth?): String? {
            val data = arrayOfNulls<String>(ARRAY_LENGTH)
            getMeasureData(locale, unit, width, "", data)
            return data[DNAM_INDEX]
        }
        @JvmStatic fun forCurrencyLongNames(locale: ULocale?, currency: Currency?, rules: PluralRules?, parent: MicroPropsGenerator?): LongNameHandler {
            val data = arrayOfNulls<String>(ARRAY_LENGTH)
            LongNameData.getCurrencyLongNameData(locale, currency, data)
            return LongNameHandler(mutableMapOf(), rules, parent).also { it.simpleFormatsToModifiers(data, NumberFormatField.CURRENCY) }
        }
        @JvmStatic fun forMeasureUnit(locale: ULocale?, unit: MeasureUnit?, width: UnitWidth?, unitDisplayCase: String?, rules: PluralRules?, parent: MicroPropsGenerator?): LongNameHandler {
            if (unit!!.getType() != null) {
                val data = arrayOfNulls<String>(ARRAY_LENGTH)
                getMeasureData(locale, unit, width, unitDisplayCase, data)
                maybeCalculateGender(locale, unit, data)
                return LongNameHandler(mutableMapOf(), rules, parent).also {
                    it.simpleFormatsToModifiers(data, NumberFormatField.MEASURE_UNIT)
                    if (data[GENDER_INDEX] != null) it.gender = data[GENDER_INDEX]
                }
            }
            assertLongNameHandler({ unit.getComplexity() != MeasureUnit.Complexity.MIXED }) {
                "Mixed units not supported by LongNameHandler: use MixedUnitLongNameHandler"
            }
            return forArbitraryUnit(locale, unit, width, unitDisplayCase, rules, parent)
        }

        private fun forArbitraryUnit(locale: ULocale?, input: MeasureUnit, width: UnitWidth?, case: String?, rules: PluralRules?, parent: MicroPropsGenerator?): LongNameHandler {
            val full = input.getCopyOfMeasureUnitImpl()
            var unit: MeasureUnit? = null
            var perUnit: MeasureUnit? = null
            if (full.getConstantDenominator() != 0L) {
                perUnit = MeasureUnitImpl().also { it.setConstantDenominator(full.getConstantDenominator()) }.build()
            }
            for (sub in full.getSingleUnits()) {
                if (sub.getDimensionality() > 0) unit = if (unit == null) sub.build() else unit.product(sub.build())
                else {
                    sub.setDimensionality(sub.getDimensionality() * -1)
                    perUnit = if (perUnit == null) sub.build() else perUnit.product(sub.build())
                }
            }
            val numerator = unit?.getCopyOfMeasureUnitImpl()
            val denominator = perUnit?.getCopyOfMeasureUnitImpl()
            val cases = DerivedComponents(locale, "case", "per")
            val numData = arrayOfNulls<String>(ARRAY_LENGTH)
            processPatternTimes(numerator, locale, width, cases.value0(case), numData)
            val denData = arrayOfNulls<String>(ARRAY_LENGTH)
            processPatternTimes(denominator, locale, width, cases.value1(case), denData)
            val perPattern = denData[PER_INDEX] ?: run {
                val sb = StringBuilder()
                val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(getCompoundValue("per", locale, width), sb, 2, 2)
                val denCompiled = SimpleFormatterImpl.compileToStringMinMaxArguments(getWithPlural(denData, StandardPlural.ONE), sb, 0, 1)
                val denString = PatternProps.trimSpaceChar(SimpleFormatterImpl.getTextWithNoArguments(denCompiled))
                SimpleFormatterImpl.formatCompiledPattern(compiled, "{0}", denString)
            }
            val result = LongNameHandler(mutableMapOf(), rules, parent)
            if (perPattern.isEmpty()) result.simpleFormatsToModifiers(numData, NumberFormatField.MEASURE_UNIT)
            else result.multiSimpleFormatsToModifiers(numData, perPattern, NumberFormatField.MEASURE_UNIT)
            result.gender = getDerivedGender(locale, "per", numData, denData)
            return result
        }

        internal fun calculateGenderForUnit(locale: ULocale?, unit: MeasureUnit?): String {
            val impl = unit!!.getCopyOfMeasureUnitImpl()
            val singles = impl.getSingleUnits()
            var index = 0
            if (impl.getComplexity() == MeasureUnit.Complexity.COMPOUND) {
                var start = 0
                var end = singles.size - 1
                assertLongNameHandler({ end > 0 }) { "COMPOUND units have more than one single unit" }
                if (singles[end].getDimensionality() < 0) {
                    val rule = getDeriveCompoundRule(locale, "gender", "per")
                    if (rule.length != 1) return rule
                    if (rule[0] == '1') while (singles[start].getDimensionality() >= 0) start++
                    else {
                        while (end >= 0 && singles[end].getDimensionality() < 0) end--
                        if (end < 0) return ""
                    }
                }
                if (end > start) {
                    val rule = getDeriveCompoundRule(locale, "gender", "times")
                    if (rule.length != 1) return rule
                    if (rule[0] == '0') end = start else start = end
                }
                assertLongNameHandler({ start == end }) { null }
                index = start
            } else if (impl.getComplexity() == MeasureUnit.Complexity.MIXED) {
                throw ICUException("calculateGenderForUnit does not support MIXED units")
            } else {
                assertLongNameHandler({ impl.getComplexity() == MeasureUnit.Complexity.SINGLE }) { null }
                assertLongNameHandler({ singles.size == 1 }) { null }
            }
            val single = singles[index]
            if (abs(single.getDimensionality()) != 1) {
                val rule = getDeriveCompoundRule(locale, "gender", "power")
                if (rule.length != 1) return rule
            }
            // Source tests dimensionality again here, rather than the prefix.
            if (abs(single.getDimensionality()) != 1) {
                val rule = getDeriveCompoundRule(locale, "gender", "prefix")
                if (rule.length != 1) return rule
            }
            return getGenderForBuiltin(locale, MeasureUnit.forIdentifier(single.getSimpleUnitID()))
        }
        private fun maybeCalculateGender(locale: ULocale?, unit: MeasureUnit?, out: Array<String?>) {
            if (out[GENDER_INDEX] == null) {
                if (getGenderForBuiltin(locale, MeasureUnit.METER).isEmpty()) return
                out[GENDER_INDEX] = calculateGenderForUnit(locale, unit)
            }
        }

        internal fun processPatternTimes(product: MeasureUnitImpl?, locale: ULocale?, width: UnitWidth?, initialCase: String?, out: Array<String?>) {
            assertLongNameHandler({ out[StandardPlural.OTHER.ordinal] == null }) { "outArray must have only null values!" }
            assertLongNameHandler({ out[PER_INDEX] == null }) { "outArray must have only null values!" }
            if (product == null) { out[StandardPlural.OTHER.ordinal] = ""; out[PER_INDEX] = ""; return }
            if (product.getComplexity() == MeasureUnit.Complexity.MIXED) throw UnsupportedOperationException("Mixed units not supported by LongNameHandler")
            if (product.getIdentifier() == null) product.serialize()
            if (product.getIdentifier()!!.isEmpty()) return
            val builtin = MeasureUnit.findBySubType(product.getIdentifier())
            if (builtin != null) {
                getMeasureData(locale, builtin, width, initialCase, out)
                maybeCalculateGender(locale, builtin, out)
                return
            }
            val sb = StringBuilder()
            val times = SimpleFormatterImpl.compileToStringMinMaxArguments(getCompoundValue("times", locale, width), sb, 2, 2)
            val placeholders = arrayOfNulls<PlaceholderPosition>(ARRAY_LENGTH)
            var joiner = '\u0000'
            for (plural in StandardPlural.entries) out[plural.ordinal] = if (plural == StandardPlural.OTHER) "" else null
            var pluralCategory: String? = null
            var case = initialCase
            val timesPlurals = DerivedComponents(locale, "plural", "times")
            val timesCases = DerivedComponents(locale, "case", "times")
            val powerCases = DerivedComponents(locale, "case", "power")
            val singles = product.getSingleUnits()
            var index = 0
            while (index < singles.size) {
                val single = singles[index]
                val singlePlural: String?
                var singleCase: String?
                if (index < singles.size - 1) {
                    singlePlural = timesPlurals.value0(pluralCategory)
                    singleCase = timesCases.value0(case)
                    pluralCategory = timesPlurals.value1(pluralCategory)
                    case = timesCases.value1(case)
                } else {
                    singlePlural = timesPlurals.value1(pluralCategory)
                    singleCase = timesCases.value1(case)
                }
                val simple = MeasureUnit.findBySubType(single.getSimpleUnitID())
                    ?: throw UnsupportedOperationException("Unsupported sinlgeUnit: " + single.getSimpleUnitID())
                val gender = getGenderForBuiltin(locale, simple)
                assertLongNameHandler({ single.getDimensionality() > 0 }) { null }
                val dimensionality = single.getDimensionality()
                val powerPatterns = arrayOfNulls<String>(ARRAY_LENGTH)
                if (dimensionality != 1) {
                    try { getInflectedMeasureData("compound/power$dimensionality", locale, width, gender, singleCase, powerPatterns) }
                    catch (e: MissingResourceException) {
                        if (dimensionality > 3) throw UnsupportedOperationException("powerN not supported for N > 3: " + product.getIdentifier())
                        else throw e
                    }
                    singleCase = powerCases.value0(singleCase)
                    single.setDimensionality(1)
                }
                val prefix = single.getPrefix()
                var prefixPattern = ""
                if (prefix != MeasurePrefix.ONE) {
                    prefixPattern = getCompoundValue("${prefix!!.getBase()}p${prefix.getPower()}", locale, width)
                    single.setPrefix(MeasurePrefix.ONE)
                }
                val data = arrayOfNulls<String>(ARRAY_LENGTH)
                assertLongNameHandler({ single.build().getIdentifier()!!.equals(single.getSimpleUnitID()) }) {
                    "Should be equal: singleUnit.build().getIdentifier() produced " + single.build().getIdentifier() +
                        ", singleUnit.getSimpleUnitID() produced " + single.getSimpleUnitID()
                }
                getMeasureData(locale, single.build(), width, singleCase, data)
                if (data[GENDER_INDEX] != null) {
                    assertLongNameHandler({ data[GENDER_INDEX]!!.isNotEmpty() }) { null }
                    if (prefix != MeasurePrefix.ONE) data[GENDER_INDEX] = getDerivedGender(locale, "prefix", data, null)
                    if (dimensionality != 1) data[GENDER_INDEX] = getDerivedGender(locale, "power", data, null)
                    val rule = getDeriveCompoundRule(locale, "gender", "times")
                    if (rule.length == 1) {
                        if ((rule[0] == '0' && index == 0) || (rule[0] == '1' && index == singles.size - 1)) {
                            assertLongNameHandler({ out[GENDER_INDEX] == null }) { null }
                            out[GENDER_INDEX] = data[GENDER_INDEX]
                        }
                    } else if (out[GENDER_INDEX] == null) out[GENDER_INDEX] = rule
                }
                for (pluralValue in StandardPlural.entries) {
                    var plural = pluralValue
                    val p = plural.ordinal
                    if (out[p] == null) {
                        if (data[p] == null) continue
                        out[p] = getWithPlural(out, plural)
                    }
                    if (singlePlural != null) plural = StandardPlural.fromString(singlePlural)
                    val extracted = LongNameData.extractCorePattern(getWithPlural(data, plural))
                    var core = extracted.coreUnit
                    if (extracted.placeholderPosition == PlaceholderPosition.MIDDLE) throw UnsupportedOperationException()
                    if (placeholders[p] == null) { placeholders[p] = extracted.placeholderPosition; joiner = extracted.joinerChar }
                    else assertLongNameHandler({ placeholders[p] == extracted.placeholderPosition }) { null }
                    if (prefix != MeasurePrefix.ONE) {
                        val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(prefixPattern, sb, 1, 1)
                        if (width == UnitWidth.FULL_NAME) core = CaseMap.toLower().apply(locale, core)
                        core = SimpleFormatterImpl.formatCompiledPattern(compiled, core)
                    }
                    if (dimensionality != 1) {
                        val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(getWithPlural(powerPatterns, plural), sb, 1, 1)
                        if (width == UnitWidth.FULL_NAME) core = CaseMap.toLower().apply(locale, core)
                        core = SimpleFormatterImpl.formatCompiledPattern(compiled, core)
                    }
                    out[p] = if (out[p]!!.isEmpty()) core else SimpleFormatterImpl.formatCompiledPattern(times, out[p], core)
                }
                index++
            }
            if (product.getConstantDenominator() != 0L) {
                out[CONSTANT_DENOMINATOR_INDEX] = product.getConstantDenominator().toString()
                var pluralIndex: Int? = null
                for (plural in StandardPlural.entries) if (out[plural.ordinal] != null) { pluralIndex = plural.ordinal; break }
                assertLongNameHandler({ pluralIndex != null }) { "No plural form found for constant denominator" }
                val p = pluralIndex!!
                out[p] = if (out[p]!!.isEmpty()) out[CONSTANT_DENOMINATOR_INDEX]
                else SimpleFormatterImpl.formatCompiledPattern(times, out[CONSTANT_DENOMINATOR_INDEX], out[p])
            }
            for (plural in StandardPlural.entries) {
                val p = plural.ordinal
                if (placeholders[p] == PlaceholderPosition.BEGINNING) {
                    out[p] = "{0}" + (if (joiner != '\u0000') joiner.toString() else "") + out[p]
                } else if (placeholders[p] == PlaceholderPosition.END) {
                    if (joiner != '\u0000') out[p] = out[p] + joiner
                    out[p] = out[p] + "{0}"
                }
            }
        }
    }
}

internal expect fun assertLongNameHandler(condition: () -> Boolean, message: () -> String?)
