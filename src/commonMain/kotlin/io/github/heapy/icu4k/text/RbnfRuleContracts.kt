// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Internal rule operations; the NFRule/NFSubstitution port supplies their implementation. */
internal interface RbnfRule {
    fun getBaseValue(): Long
    fun setBaseValue(value: Long)
    fun getDecimalPoint(): Char
    fun shouldRollBack(number: Long): Boolean
    fun doFormat(number: Long, output: StringBuilder?, position: Int, recursionCount: Int)
    fun doFormat(number: Double, output: StringBuilder?, position: Int, recursionCount: Int)
    fun doParse(text: String?, position: ParsePosition?, fractionRule: Boolean, upperBound: Double, executedMask: Int, recursionCount: Int): Number?
    fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?)
}

/** The owner supplies rule construction and lazy special rules, preserving two-phase initialization. */
internal interface RbnfRuleSetOwner {
    fun getDecimalFormatSymbols(): DecimalFormatSymbols
    fun getDefaultNaNRule(): RbnfRule
    fun getDefaultInfinityRule(): RbnfRule
    fun makeRules(description: String, ruleSet: RbnfRuleSetCore, predecessor: RbnfRule?, rules: MutableList<RbnfRule>)
}
