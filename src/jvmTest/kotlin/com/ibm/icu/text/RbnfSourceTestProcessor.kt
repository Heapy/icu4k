// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("EXPOSED_PARAMETER_TYPE", "EXPOSED_SUPER_CLASS")
package com.ibm.icu.text

/** Test-only implementation in the source interface's package, which has package visibility. */
internal class RbnfSourceTestProcessor : RBNFPostProcessor {
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
    override fun process(output: StringBuilder?, ruleSet: NFRuleSet?) {
        ++calls
        if (failure && calls == 1) throw IllegalStateException("processor process")
        output!!.append("[").append(calls).append(':').append(ruleSet!!.getName()).append(':').append(owner!!.defaultRuleSetName).append(']')
    }
    companion object {
        var initializations = 0
        var lastOwner: RuleBasedNumberFormat? = null
        var failConstructor = false
    }
}
