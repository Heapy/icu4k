// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2000-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal class TransliterationRuleSet {
    private val ruleVector = mutableListOf<TransliterationRule>()
    private var maxContextLength = 0
    private var rules: Array<TransliterationRule>? = null
    private var index: IntArray? = null
    fun getMaximumContextLength(): Int = maxContextLength
    fun addRule(rule: TransliterationRule) {
        ruleVector.add(rule)
        val len = rule.getAnteContextLength()
        if (len > maxContextLength) maxContextLength = len
        rules = null
    }
    fun freeze() {
        val indices = IntArray(257)
        index = indices
        val collected = ArrayList<TransliterationRule>(2 * ruleVector.size)
        val values = IntArray(ruleVector.size) { ruleVector[it].getIndexValue() }
        for (x in 0..255) {
            indices[x] = collected.size
            for (j in ruleVector.indices) {
                if (values[j] >= 0) {
                    if (values[j] == x) collected.add(ruleVector[j])
                } else if (ruleVector[j].matchesIndexValue(x)) collected.add(ruleVector[j])
            }
        }
        indices[256] = collected.size
        val frozen = collected.toTypedArray()
        rules = frozen
        var errors: StringBuilder? = null
        for (x in 0..255) for (j in indices[x] until indices[x + 1] - 1) {
            for (k in j + 1 until indices[x + 1]) if (frozen[j].masks(frozen[k])) {
                if (errors == null) errors = StringBuilder() else errors.append('\n')
                errors.append("Rule ").append(frozen[j]).append(" masks ").append(frozen[k])
            }
        }
        if (errors != null) throw IllegalArgumentException(errors.toString())
    }
    fun transliterate(text: Replaceable, pos: TransliterationPosition, incremental: Boolean): Boolean {
        val indexByte = text.char32At(pos.start) and 0xff
        val indices = index!!
        for (i in indices[indexByte] until indices[indexByte + 1]) {
            when (rules!![i].matchAndReplace(text, pos, incremental)) {
                UnicodeMatcher.U_MATCH -> return true
                UnicodeMatcher.U_PARTIAL_MATCH -> return false
            }
        }
        pos.start += UTF16.getCharCount(text.char32At(pos.start))
        return true
    }
    /** RuleBasedTransliterator's loop guard; a shared Data owner must serialize access. */
    fun handleTransliterate(text: Replaceable, pos: TransliterationPosition, incremental: Boolean) {
        var loopCount = 0
        var loopLimit = (pos.limit - pos.start) shl 4
        if (loopLimit < 0) loopLimit = Int.MAX_VALUE
        while (pos.start < pos.limit && loopCount <= loopLimit && transliterate(text, pos, incremental)) ++loopCount
    }
    fun toRules(escapeUnprintable: Boolean): String = ruleVector.joinToString("\n") { it.toRule(escapeUnprintable) }
    fun addSourceTargetSet(filter: UnicodeSet, sourceSet: UnicodeSet, targetSet: UnicodeSet) {
        val currentFilter = UnicodeSet(filter)
        val revisiting = UnicodeSet()
        for (rule in ruleVector) {
            rule.addSourceTargetSet(currentFilter, sourceSet, targetSet, revisiting.clear())
            currentFilter.addAll(revisiting)
        }
    }
}
