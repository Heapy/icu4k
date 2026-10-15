// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Source package-level extension protocol for class-name-based RBNF postprocessing. */
internal interface RBNFPostProcessor {
    fun init(formatter: RuleBasedNumberFormat?, rules: String?)
    fun process(output: StringBuilder?, ruleSet: RbnfRuleSetCore?)
}
internal class RbnfFacadePostProcessorAdapter(private val delegate: RBNFPostProcessor) : RbnfCorePostProcessor {
    override fun init(formatter: RbnfFormatterCore, rules: String) { delegate.init(formatter.facadeOwner!!, rules) }
    override fun process(output: StringBuilder, ruleSet: RbnfRuleSetCore?) { delegate.process(output, ruleSet) }
}
