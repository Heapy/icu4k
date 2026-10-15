// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.CollationRoot
import io.github.heapy.icu4k.util.CloneNotSupportedException
import io.github.heapy.icu4k.util.Freezable
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.VersionInfo
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmName

/** Locale-sensitive collation contract. Concrete rule collators implement the mutable attributes. */
abstract class Collator protected constructor() : CollatorCloneBase(), Comparator<Any?>, Freezable<Collator> {
    override fun equals(other: Any?): Boolean = this === other || (other != null && this::class == other::class)
    override fun hashCode(): Int = 0
    private fun checkNotFrozen() { if (isFrozen()) throw UnsupportedOperationException("Attempt to modify frozen Collator") }
    open fun setStrength(newStrength: Int) { checkNotFrozen() }
    @Deprecated("This API is ICU internal only.")
    open fun setStrength2(newStrength: Int): Collator { setStrength(newStrength); return this }
    open fun setDecomposition(decomposition: Int) { checkNotFrozen() }
    open fun setReorderCodes(order: IntArray?) { throw UnsupportedOperationException("Needs to be implemented by the subclass.") }
    @JvmName("setReorderCodesValues") fun setReorderCodes(vararg order: Int) = setReorderCodes(order as IntArray?)
    public override fun clone(): Collator = (cloneHost() as Collator?) ?: cloneNative()
    /** Native subclasses supply their typed shallow copy; RuleBasedCollator implements its own complete clone. */
    protected open fun cloneNative(): Collator = throw CloneNotSupportedException()
    open fun getStrength(): Int = TERTIARY
    open fun getDecomposition(): Int = NO_DECOMPOSITION
    open fun equals(source: String?, target: String?): Boolean = compare(source,target) == 0
    open fun getTailoredSet(): UnicodeSet = UnicodeSet(0,0x10ffff)
    abstract fun compare(source: String?, target: String?): Int
    override fun compare(a: Any?, b: Any?): Int = doCompare(a as CharSequence?,b as CharSequence?)
    protected open fun doCompare(left: CharSequence?, right: CharSequence?): Int = compare(left!!.toString(),right!!.toString())
    abstract fun getCollationKey(source: String?): CollationKey?
    abstract fun getRawCollationKey(source: String?, key: RawCollationKey?): RawCollationKey?
    open fun setMaxVariable(group: Int): Collator = throw UnsupportedOperationException("Needs to be implemented by the subclass.")
    open fun getMaxVariable(): Int = ReorderCodes.PUNCTUATION
    @Deprecated("Call setMaxVariable instead.") abstract fun setVariableTop(varTop: String?): Int
    abstract fun getVariableTop(): Int
    @Deprecated("Call setMaxVariable instead.") abstract fun setVariableTop(varTop: Int)
    abstract fun getVersion(): VersionInfo
    abstract fun getUCAVersion(): VersionInfo
    open fun getReorderCodes(): IntArray = throw UnsupportedOperationException("Needs to be implemented by the subclass.")
    override fun isFrozen(): Boolean = false
    override fun freeze(): Collator = throw UnsupportedOperationException("Needs to be implemented by the subclass.")
    override fun cloneAsThawed(): Collator = throw UnsupportedOperationException("Needs to be implemented by the subclass.")
    open fun getLocale(type: ULocale.Type?): ULocale? = ULocale.ROOT
    internal open fun setLocale(valid: ULocale?, actual: ULocale?) {}
    interface ReorderCodes {
        companion object {
            const val DEFAULT = -1
            const val NONE = 103
            const val OTHERS = 103
            const val SPACE = 0x1000
            const val FIRST = SPACE
            const val PUNCTUATION = 0x1001
            const val SYMBOL = 0x1002
            const val CURRENCY = 0x1003
            const val DIGIT = 0x1004
            @Deprecated("The numeric value may change over time.") const val LIMIT = 0x1005
        }
    }
    companion object {
        const val PRIMARY = 0
        const val SECONDARY = 1
        const val TERTIARY = 2
        const val QUATERNARY = 3
        const val IDENTICAL = 15
        const val FULL_DECOMPOSITION = IDENTICAL
        const val NO_DECOMPOSITION = 16
        const val CANONICAL_DECOMPOSITION = 17
        @JvmStatic fun getEquivalentReorderCodes(reorderCode: Int): IntArray = CollationRoot.getData().getEquivalentScripts(reorderCode)
    }
}
