// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Original compiler front-end state, consumed by category and DFA construction. */
internal class RBBIRuleParseResult(val fRules: String, val fDebugEnv: String? = null) {
    val fStrippedRules = StringBuilder(fRules)
    val fTreeRoots = arrayOfNulls<RBBINode>(4)
    var fDefaultTree = 0
    var fChainRules = false
    var fLookAheadHardBreak = false
    val fUSetNodes = ArrayList<RBBINode>()
    val fStatusSets = HashMap<Set<Int>, Int>()
    val fRuleStatusVals = ArrayList<Int>()
}
