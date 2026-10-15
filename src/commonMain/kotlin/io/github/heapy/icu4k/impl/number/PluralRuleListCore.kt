// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.DecimalQuantityCore.Operand
import io.github.heapy.icu4k.impl.number.PluralCondition.SampleType

/** Parsed rules in source order, with the unconditional 'other' rule moved last. */
internal class PluralRuleListCore private constructor(private val rules: List<Rule>, val hasExplicitBoundingInfo: Boolean) {
    private class Rule(val keyword: String, val condition: PluralCondition?, val integers: PluralSamplesCore?, val decimals: PluralSamplesCore?) {
        override fun toString() = keyword + ": " + condition!!.toString() +
            (integers?.let { " $it" } ?: "") + (decimals?.let { " $it" } ?: "")
    }
    fun select(number: (Operand) -> Double, infinite: Boolean = false, nan: Boolean = false): String {
        if (infinite || nan) return "other"
        return rules.first { it.condition!!.evaluate(number) }.keyword
    }
    fun select(number: (Operand) -> Double, keyword: String): Boolean =
        rules.any { it.keyword == keyword && it.condition!!.evaluate(number) }
    fun getKeywords(): Set<String> = rules.mapTo(linkedSetOf()) { it.keyword }
    fun getRules(keyword: String): String? = rules.firstOrNull { it.keyword == keyword }?.let { it.condition!!.toString() }
    fun getDecimalSamples(keyword: String, type: SampleType): PluralSamplesCore? =
        rules.firstOrNull { it.keyword == keyword }?.let { if (type == SampleType.INTEGER) it.integers else it.decimals }
    fun computeLimited(keyword: String, type: SampleType): Boolean =
        rules.firstOrNull { it.keyword == keyword }?.let { it.condition!!.isLimited(type) } ?: false
    fun isLimited(keyword: String, type: SampleType): Boolean =
        if (hasExplicitBoundingInfo) getDecimalSamples(keyword, type)?.bounded ?: true else computeLimited(keyword, type)
    override fun toString(): String = rules.joinToString(";  ")

    companion object {
        fun parse(description: String): PluralRuleListCore {
            val trimmed = description.trim { it <= ' ' }
            val chain = if (trimmed.endsWith(';')) trimmed.dropLast(1) else trimmed
            val rules = mutableListOf<Rule>()
            var explicitBounds = false
            for (text in splitPluralText(chain, ";")) {
                val rule = parseRule(text.trim { it <= ' ' })
                explicitBounds = explicitBounds || rule.integers != null || rule.decimals != null
                require(rules.none { it.keyword == rule.keyword }) { "Duplicate keyword: ${rule.keyword}" }
                rules.add(rule)
            }
            val other = rules.firstOrNull { it.keyword == "other" } ?: parseRule("other:")
            rules.remove(other)
            rules.add(other)
            return PluralRuleListCore(rules, explicitBounds)
        }
        private fun parseRule(input: String): Rule {
            if (input.isEmpty()) return Rule("other", PluralConditionParser.parse("n"), null, null)
            val description = input.lowercase()
            val colon = description.indexOf(':')
            if (colon < 0) throw PluralConditionParseException("missing ':' in rule description '$description'", 0)
            val keyword = description.substring(0, colon).trim { it <= ' ' }
            if (!keyword.all { it in 'a'..'z' }) throw PluralConditionParseException("keyword '$keyword is not valid", 0)
            val body = description.substring(colon + 1).trim { it <= ' ' }
            val parts = splitPluralText(body, "@").toTypedArray()
            var integers: PluralSamplesCore? = null
            var decimals: PluralSamplesCore? = null
            when (parts.size) {
                1 -> Unit
                2 -> {
                    val samples = PluralSamplesCore.parse(parts[1])
                    if (samples.sampleType == SampleType.INTEGER) integers = samples else decimals = samples
                }
                3 -> {
                    integers = PluralSamplesCore.parse(parts[1]); decimals = PluralSamplesCore.parse(parts[2])
                    require(integers.sampleType == SampleType.INTEGER && decimals.sampleType == SampleType.DECIMAL) { "Must have @integer then @decimal in $body" }
                }
                else -> throw IllegalArgumentException("Too many samples in $body")
            }
            val other = keyword == "other"
            require(other == parts[0].isEmpty()) { "The keyword 'other' must have no constraints, just samples." }
            return Rule(keyword, if (other) PluralConditionParser.parse("n") else PluralConditionParser.parse(parts[0]), integers, decimals)
        }
    }
}
