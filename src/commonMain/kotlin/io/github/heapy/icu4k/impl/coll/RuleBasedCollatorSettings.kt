// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.lang.UScript

/** RuleBasedCollator's configuration transitions, including source COW boundaries. */
internal class RuleBasedCollatorSettings(
    private val data: () -> CollationData?,
    private val settings: () -> SharedObject.Reference<CollationSettings>?,
    private val defaultSettings: () -> CollationSettings?,
    private val isFrozen: () -> Boolean,
    private val assertionsEnabled: Boolean = collationIteratorClassAssertionsEnabled(RuleBasedCollatorSettings::class),
) {
    private fun read(): CollationSettings = settings()!!.readOnly()!!
    private fun owned(): CollationSettings = settings()!!.copyOnWrite()
    private fun checkNotFrozen() { if (isFrozen()) throw UnsupportedOperationException("Attempt to modify frozen RuleBasedCollator") }
    private fun setFastLatinOptions(value: CollationSettings) {
        value.fastLatinOptions = CollationFastLatin.getOptions(data(),value,value.fastLatinPrimaries)
    }
    fun setHiraganaQuaternary(flag: Boolean) { checkNotFrozen() }
    fun setHiraganaQuaternaryDefault() { checkNotFrozen() }
    fun setUpperCaseFirst(upperfirst: Boolean) {
        checkNotFrozen()
        if (upperfirst == isUpperCaseFirst()) return
        val value = owned()
        value.setCaseFirst(if (upperfirst) CollationSettings.CASE_FIRST_AND_UPPER_MASK else 0)
        setFastLatinOptions(value)
    }
    fun setLowerCaseFirst(lowerfirst: Boolean) {
        checkNotFrozen()
        if (lowerfirst == isLowerCaseFirst()) return
        val value = owned()
        value.setCaseFirst(if (lowerfirst) CollationSettings.CASE_FIRST else 0)
        setFastLatinOptions(value)
    }
    private fun setDefault(update: (CollationSettings, Int) -> Unit) {
        checkNotFrozen()
        val defaults = defaultSettings()
        if (settings()!!.readOnly() === defaults) return
        val value = owned()
        update(value,defaults!!.options)
        setFastLatinOptions(value)
    }
    fun setCaseFirstDefault() = setDefault { value, options -> value.setCaseFirstDefault(options) }
    fun setAlternateHandlingDefault() = setDefault { value, options -> value.setAlternateHandlingDefault(options) }
    fun setCaseLevelDefault() = setDefault { value, options -> value.setFlagDefault(CollationSettings.CASE_LEVEL,options) }
    fun setDecompositionDefault() = setDefault { value, options -> value.setFlagDefault(CollationSettings.CHECK_FCD,options) }
    fun setFrenchCollationDefault() = setDefault { value, options -> value.setFlagDefault(CollationSettings.BACKWARD_SECONDARY,options) }
    fun setStrengthDefault() = setDefault { value, options -> value.setStrengthDefault(options) }
    fun setNumericCollationDefault() = setDefault { value, options -> value.setFlagDefault(CollationSettings.NUMERIC,options) }
    fun setFrenchCollation(flag: Boolean) {
        checkNotFrozen(); if (flag == isFrenchCollation()) return
        val value = owned(); value.setFlag(CollationSettings.BACKWARD_SECONDARY,flag); setFastLatinOptions(value)
    }
    fun setAlternateHandlingShifted(shifted: Boolean) {
        checkNotFrozen(); if (shifted == isAlternateHandlingShifted()) return
        val value = owned(); value.setAlternateHandlingShifted(shifted); setFastLatinOptions(value)
    }
    fun setCaseLevel(flag: Boolean) {
        checkNotFrozen(); if (flag == isCaseLevel()) return
        val value = owned(); value.setFlag(CollationSettings.CASE_LEVEL,flag); setFastLatinOptions(value)
    }
    fun setDecomposition(decomposition: Int) {
        checkNotFrozen()
        val flag = when (decomposition) { 16 -> false; 17 -> true; else -> throw IllegalArgumentException("Wrong decomposition mode.") }
        if (flag == read().getFlag(CollationSettings.CHECK_FCD)) return
        val value = owned(); value.setFlag(CollationSettings.CHECK_FCD,flag); setFastLatinOptions(value)
    }
    fun setStrength(newStrength: Int) {
        checkNotFrozen(); if (newStrength == getStrength()) return
        val value = owned(); value.setStrength(newStrength); setFastLatinOptions(value)
    }
    fun setMaxVariable(group: Int) {
        var actualGroup = group
        val number = if (group == -1) -1 else if (group in FIRST..CURRENCY) group - FIRST
            else throw IllegalArgumentException("illegal max variable group $group")
        val oldValue = read().getMaxVariable()
        if (number == oldValue) return
        val defaults = defaultSettings()
        if (settings()!!.readOnly() === defaults && number < 0) return
        // Deliberately no frozen check: the ICU4J method does not call it.
        val value = owned()
        if (group == -1) actualGroup = FIRST + defaults!!.getMaxVariable()
        val varTop = data()!!.getLastPrimaryForGroup(actualGroup)
        checkAssertion { varTop != 0L }
        value.setMaxVariable(number,defaults!!.options)
        value.variableTop = varTop
        setFastLatinOptions(value)
    }
    fun setVariableTop(varTop: String?): Int {
        checkNotFrozen()
        if (varTop == null || varTop.isEmpty()) throw IllegalArgumentException("Variable top argument string can not be null or zero in length.")
        val numeric = read().isNumeric()
        val ci = if (read().dontCheckFCD()) UTF16CollationIterator(data(),numeric,varTop,0)
            else FCDUTF16CollationIterator(data(),numeric,varTop,0)
        val ce1 = ci.nextCE(); val ce2 = ci.nextCE()
        if (ce1 == Collation.NO_CE || ce2 != Collation.NO_CE) throw IllegalArgumentException("Variable top argument string must map to exactly one collation element")
        internalSetVariableTop(ce1 ushr 32)
        return read().variableTop.toInt()
    }
    fun setVariableTop(varTop: Int) { checkNotFrozen(); internalSetVariableTop(varTop.toLong() and 0xffffffffL) }
    private fun internalSetVariableTop(varTop: Long) {
        if (varTop != read().variableTop) {
            val group = data()!!.getGroupForPrimary(varTop)
            if (group < FIRST || CURRENCY < group) throw IllegalArgumentException("The variable top must be a primary weight in the space/punctuation/symbols/currency symbols range")
            val v = data()!!.getLastPrimaryForGroup(group)
            checkAssertion { v != 0L && v >= varTop }
            if (v != read().variableTop) {
                val value = owned()
                value.setMaxVariable(group-FIRST,defaultSettings()!!.options)
                value.variableTop = v
                setFastLatinOptions(value)
            }
        }
    }
    fun setNumericCollation(flag: Boolean) {
        checkNotFrozen(); if (flag == getNumericCollation()) return
        val value = owned(); value.setFlag(CollationSettings.NUMERIC,flag); setFastLatinOptions(value)
    }
    fun setReorderCodes(order: IntArray?) {
        checkNotFrozen()
        var length = order?.size ?: 0
        if (length == 1 && order!![0] == UScript.UNKNOWN) length = 0
        if (if (length == 0) read().reorderCodes!!.isEmpty() else order.contentEquals(read().reorderCodes)) return
        val defaults = defaultSettings()
        if (length == 1 && order!![0] == -1) {
            if (settings()!!.readOnly() !== defaults) {
                val value = owned(); value.copyReorderingFrom(defaults!!); setFastLatinOptions(value)
            }
            return
        }
        val value = owned()
        if (length == 0) value.resetReordering() else value.setReordering(data(),order!!.copyOf())
        setFastLatinOptions(value)
    }
    fun getStrength(): Int = read().getStrength()
    fun getDecomposition(): Int = if ((read().options and CollationSettings.CHECK_FCD) != 0) 17 else 16
    fun isUpperCaseFirst(): Boolean = read().getCaseFirst() == CollationSettings.CASE_FIRST_AND_UPPER_MASK
    fun isLowerCaseFirst(): Boolean = read().getCaseFirst() == CollationSettings.CASE_FIRST
    fun isAlternateHandlingShifted(): Boolean = read().getAlternateHandling()
    fun isCaseLevel(): Boolean = (read().options and CollationSettings.CASE_LEVEL) != 0
    fun isFrenchCollation(): Boolean = (read().options and CollationSettings.BACKWARD_SECONDARY) != 0
    fun isHiraganaQuaternary(): Boolean = false
    fun getVariableTop(): Int = read().variableTop.toInt()
    fun getNumericCollation(): Boolean = (read().options and CollationSettings.NUMERIC) != 0
    fun getReorderCodes(): IntArray = read().reorderCodes!!.copyOf()
    fun getMaxVariable(): Int = FIRST + read().getMaxVariable()
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    companion object { private const val FIRST = 0x1000; private const val CURRENCY = 0x1003 }
}
