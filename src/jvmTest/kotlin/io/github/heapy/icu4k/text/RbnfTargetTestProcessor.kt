// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal class RbnfTargetTestProcessor : RBNFPostProcessor {
    private var owner: RuleBasedNumberFormat? = null
    private var failure = false
    private var calls = 0
    init { if (failConstructor) throw AssertionError("processor constructor") }
    override fun init(formatter: RuleBasedNumberFormat?, rules: String?) {
        owner = formatter
        lastOwner = formatter
        ++initializations
        formatter!!.setDefaultRuleSet("%other")
        failure = rules!!.contains("processFailure")
        if (rules.contains("initFailure")) throw IllegalStateException("processor init")
    }
    override fun process(output: StringBuilder?, ruleSet: RbnfRuleSetCore?) {
        ++calls
        if (failure && calls == 1) throw IllegalStateException("processor process")
        output!!.append("[").append(calls).append(':').append(ruleSet!!.getName()).append(':').append(owner!!.getDefaultRuleSetName()).append(']')
    }
    companion object {
        var initializations = 0
        var lastOwner: RuleBasedNumberFormat? = null
        var failConstructor = false
    }
}
