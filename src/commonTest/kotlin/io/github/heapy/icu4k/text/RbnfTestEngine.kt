// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.impl.PatternProps

/** Test graph construction only; public RBNF initialization/localization is a separate port. */
internal class RbnfTestEngine(descriptions: Array<String>, locale: ULocale = ULocale.ENGLISH, initialDefaultName: String? = null) : RbnfSubstitutionOwner {
    val sets = LinkedHashMap<String, RbnfRuleSetCore>()
    private var symbols = DecimalFormatSymbols(locale)
    private var decimal = DecimalFormat("#,##0.###", symbols)
    var configuredRoundingMode = 7
    var lenient = false
    var scanner: RbnfLenientScanner? = null
    var defaultName: String
    private val pluralLocale = locale
    init {
        val copy = descriptions.map { input ->
            buildString {
                var start = 0
                while (start < input.length) {
                    while (start < input.length && (PatternProps.isWhiteSpace(input[start].code) || input[start] == ';')) ++start
                    val semicolon = input.indexOf(';', start)
                    if (semicolon < 0) { append(input, start, input.length); break }
                    append(input, start, semicolon + 1)
                    start = semicolon + 1
                }
            }
        }.toTypedArray()
        for (i in copy.indices) {
            val set = RbnfRuleSetCore(this, copy, i)
            sets[set.getName()] = set
        }
        defaultName = initialDefaultName ?: sets.keys.first()
        for ((i, set) in sets.values.withIndex()) set.parseRules(copy[i])
    }
    override fun getDecimalFormatSymbols() = symbols
    override fun getDefaultNaNRule() = RbnfRuleCore(this, "NaN: ${symbols.getNaN()}")
    override fun getDefaultInfinityRule() = RbnfRuleCore(this, "Inf: ${symbols.getInfinity()}")
    override fun getDecimalFormat() = decimal
    override fun getRoundingMode() = configuredRoundingMode
    override fun getDefaultRuleSet() = sets[defaultName]
    override fun findRuleSet(name: String): RbnfRuleSetCore = sets[name] ?: throw IllegalArgumentException("No rule set named $name")
    override fun lenientParseEnabled() = lenient
    override fun getLenientScanner() = scanner
    override fun makeRules(description: String, ruleSet: RbnfRuleSetCore, predecessor: RbnfRule?, rules: MutableList<RbnfRule>) {
        RbnfRuleCore.makeRules(description, ruleSet, predecessor as RbnfRuleCore?, this, rules)
    }
    override fun makeSubstitution(position: Int, rule: RbnfRuleCore, predecessor: RbnfRuleCore?, ruleSet: RbnfRuleSetCore, token: String) =
        RbnfSubstitutionCore.makeSubstitution(position, rule, predecessor, ruleSet, this, token)
    override fun createPluralFormat(type: PluralRules.PluralType, pattern: String) =
        PluralFormatCore(PluralRules.forLocale(pluralLocale, type), getDecimalFormat()).also { it.applyPattern(pattern) }
    fun format(number: Long, name: String = defaultName): String = StringBuilder().also { findRuleSet(name).format(number, it, 0, 0) }.toString()
    fun format(number: Double, name: String = defaultName): String = StringBuilder().also { findRuleSet(name).format(number, it, 0, 0) }.toString()
    companion object {
        const val DIGITS = "0: zero; 1: one; 2: two; 3: three; 4: four; 5: five; 6: six; 7: seven; 8: eight; 9: nine;"
        const val ENGLISH = "%main: -x: minus >>; x.x: << point >>; $DIGITS 10: ten; 11: eleven; 12: twelve; 13: thirteen; 14: fourteen; 15: fifteen; 16: sixteen; 17: seventeen; 18: eighteen; 19: nineteen; 20: twenty[->>]; 30: thirty[->>]; 40: forty[->>]; 50: fifty[->>]; 60: sixty[->>]; 70: seventy[->>]; 80: eighty[->>]; 90: ninety[->>]; 100: << hundred[ >>]; 1000: << thousand[ >>];"
    }
}
