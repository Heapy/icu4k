// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.*
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.VersionInfo

/** Unicode collation using compiled ICU rules and Common Kotlin data/iteration engines. */
class RuleBasedCollator private constructor(
    internal val tailoring: CollationTailoring,
    internal var settings: SharedObject.Reference<CollationSettings>,
    valid: ULocale?,
) : Collator() {
    internal var data: CollationData? = tailoring.data
    private val runtime = RuleBasedCollatorCore({ data }, { settings.readOnly() }, assertionsEnabled)
    private val config = RuleBasedCollatorSettings({ data }, { settings }, { tailoring.settings!!.readOnly() }, { isFrozen() }, assertionsEnabled)
    private val metadata = RuleBasedCollatorMetadata({ tailoring }, { data }, { settings.readOnly() }, valid, assertionsEnabled)
    private constructor(t: CollationTailoring) : this(t,t.settings!!.clone(),t.actualLocale)
    constructor(rules: String?) : this(buildTailoring(rules))
    internal constructor(t: CollationTailoring, valid: ULocale?) : this(t,t.settings!!.clone(),valid)
    override fun clone(): RuleBasedCollator = if (isFrozen()) this else cloneAsThawed()
    override fun cloneAsThawed(): RuleBasedCollator {
        val result = RuleBasedCollator(tailoring,settings.clone(),metadata.getLocale(ULocale.VALID_LOCALE))
        result.data = data
        result.metadata.copyLocaleFrom(metadata)
        return result
    }
    fun getCollationElementIterator(source: String?): CollationElementIterator { metadata.initMaxExpansions(); return CollationElementIterator(source,this) }
    fun getCollationElementIterator(source: CharacterIterator?): CollationElementIterator { metadata.initMaxExpansions(); return CollationElementIterator(source,this) }
    fun getCollationElementIterator(source: UCharacterIterator?): CollationElementIterator { metadata.initMaxExpansions(); return CollationElementIterator(source,this) }
    override fun isFrozen(): Boolean = runtime.isFrozen()
    override fun freeze(): Collator { runtime.freeze(); return this }
    @Deprecated("Removed from collation implementation.") fun setHiraganaQuaternary(flag: Boolean) = config.setHiraganaQuaternary(flag)
    @Deprecated("Removed from collation implementation.") fun setHiraganaQuaternaryDefault() = config.setHiraganaQuaternaryDefault()
    fun setUpperCaseFirst(upperfirst: Boolean) = config.setUpperCaseFirst(upperfirst)
    fun setLowerCaseFirst(lowerfirst: Boolean) = config.setLowerCaseFirst(lowerfirst)
    fun setCaseFirstDefault() = config.setCaseFirstDefault()
    fun setAlternateHandlingDefault() = config.setAlternateHandlingDefault()
    fun setCaseLevelDefault() = config.setCaseLevelDefault()
    fun setDecompositionDefault() = config.setDecompositionDefault()
    fun setFrenchCollationDefault() = config.setFrenchCollationDefault()
    fun setStrengthDefault() = config.setStrengthDefault()
    fun setNumericCollationDefault() = config.setNumericCollationDefault()
    fun setFrenchCollation(flag: Boolean) = config.setFrenchCollation(flag)
    fun setAlternateHandlingShifted(shifted: Boolean) = config.setAlternateHandlingShifted(shifted)
    fun setCaseLevel(flag: Boolean) = config.setCaseLevel(flag)
    override fun setDecomposition(decomposition: Int) = config.setDecomposition(decomposition)
    override fun setStrength(newStrength: Int) = config.setStrength(newStrength)
    override fun setMaxVariable(group: Int): RuleBasedCollator { config.setMaxVariable(group); return this }
    override fun getMaxVariable(): Int = config.getMaxVariable()
    @Deprecated("Call setMaxVariable instead.") override fun setVariableTop(varTop: String?): Int = config.setVariableTop(varTop)
    @Deprecated("Call setMaxVariable instead.") override fun setVariableTop(varTop: Int) = config.setVariableTop(varTop)
    fun setNumericCollation(flag: Boolean) = config.setNumericCollation(flag)
    override fun setReorderCodes(order: IntArray?) = config.setReorderCodes(order)
    fun getRules(): String = metadata.getRules()
    fun getRules(fullrules: Boolean): String = metadata.getRules(fullrules)
    override fun getTailoredSet(): UnicodeSet = metadata.getTailoredSet()
    fun getContractionsAndExpansions(contractions: UnicodeSet?, expansions: UnicodeSet?, addPrefixes: Boolean) = metadata.getContractionsAndExpansions(contractions,expansions,addPrefixes)
    internal fun internalAddContractions(c: Int, set: UnicodeSet?) = metadata.internalAddContractions(c,set)
    override fun getCollationKey(source: String?): CollationKey? = runtime.getCollationKey(source)
    override fun getRawCollationKey(source: String?, key: RawCollationKey?): RawCollationKey? = runtime.getRawCollationKey(source,key)
    @Deprecated("This API is ICU internal only.") fun internalGetCEs(str: CharSequence?): LongArray = runtime.internalGetCEs(str)
    override fun getStrength(): Int = config.getStrength()
    override fun getDecomposition(): Int = config.getDecomposition()
    fun isUpperCaseFirst(): Boolean = config.isUpperCaseFirst()
    fun isLowerCaseFirst(): Boolean = config.isLowerCaseFirst()
    fun isAlternateHandlingShifted(): Boolean = config.isAlternateHandlingShifted()
    fun isCaseLevel(): Boolean = config.isCaseLevel()
    fun isFrenchCollation(): Boolean = config.isFrenchCollation()
    @Deprecated("Removed from collation implementation.") fun isHiraganaQuaternary(): Boolean = config.isHiraganaQuaternary()
    override fun getVariableTop(): Int = config.getVariableTop()
    fun getNumericCollation(): Boolean = config.getNumericCollation()
    override fun getReorderCodes(): IntArray = config.getReorderCodes()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (!super.equals(other)) return false
        return metadata.hasSameBehavior((other as RuleBasedCollator).metadata)
    }
    override fun hashCode(): Int = metadata.behaviorHashCode()
    override fun compare(source: String?, target: String?): Int = doCompare(source,target)
    override fun doCompare(left: CharSequence?, right: CharSequence?): Int = runtime.doCompare(left,right)
    override fun getVersion(): VersionInfo = metadata.getVersion()
    override fun getUCAVersion(): VersionInfo = metadata.getUCAVersion()
    override fun getLocale(type: ULocale.Type?): ULocale? = metadata.getLocale(type)
    internal override fun setLocale(valid: ULocale?, actual: ULocale?) = metadata.setLocale(valid,actual)
    internal fun isUnsafe(c: Int): Boolean = metadata.isUnsafe(c)
    private companion object {
        val assertionsEnabled = collationIteratorClassAssertionsEnabled(RuleBasedCollator::class)
        fun buildTailoring(rules: String?): CollationTailoring {
            if (rules == null) throw IllegalArgumentException("Collation rules can not be null")
            val t = CollationBuilder(CollationRoot.getRoot()).parseAndBuild(rules)
            t.actualLocale = null
            return t
        }
    }
}
