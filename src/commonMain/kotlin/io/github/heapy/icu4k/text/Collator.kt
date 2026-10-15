// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.CollationRoot
import io.github.heapy.icu4k.impl.coll.CollatorService
import io.github.heapy.icu4k.impl.coll.CollationLocaleOptions
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.impl.defaultICUResourceLoader
import io.github.heapy.icu4k.util.UResourceBundle
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
    /** Override createCollator(ULocale), or the legacy JVM Locale overload. */
    abstract class CollatorFactory protected constructor() : CollatorFactoryLocaleBridge() {
        open fun visible(): Boolean = true
        override fun createCollator(loc: ULocale?): Collator? = createCollatorHost(loc)
        override fun getDisplayName(objectLocale: ULocale?, displayLocale: ULocale?): String? {
            if (visible()) {
                val supported = getSupportedLocaleIDs()
                val name = objectLocale!!.getBaseName()
                if (supported.contains(name)) return objectLocale.getDisplayName(displayLocale)
            }
            return null
        }
        abstract fun getSupportedLocaleIDs(): Set<String>
    }
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
        @JvmStatic fun getInstance(): Collator = getInstance(ULocale.getDefault())
        @JvmStatic fun getInstance(locale: ULocale?): Collator {
            val loc = locale ?: ULocale.getDefault()
            val coll = CollatorService.getInstance(loc)
            if (loc.getName() != loc.getBaseName()) {
                val sink = object : CollationLocaleOptions.CollatorSink {
                    override fun setStrength(value: Int) = coll.setStrength(value)
                    override fun setDecomposition(value: Int) = coll.setDecomposition(value)
                    override fun setReorderCodes(order: IntArray) = coll.setReorderCodes(order)
                    override fun setMaxVariable(value: Int) { coll.setMaxVariable(value) }
                }
                val rbc = if (coll is RuleBasedCollator) object : CollationLocaleOptions.RuleBasedSink {
                    override fun setFrenchCollation(value: Boolean) = coll.setFrenchCollation(value)
                    override fun setCaseLevel(value: Boolean) = coll.setCaseLevel(value)
                    override fun setLowerCaseFirst(value: Boolean) = coll.setLowerCaseFirst(value)
                    override fun setUpperCaseFirst(value: Boolean) = coll.setUpperCaseFirst(value)
                    override fun setAlternateHandlingShifted(value: Boolean) = coll.setAlternateHandlingShifted(value)
                    override fun setNumericCollation(value: Boolean) = coll.setNumericCollation(value)
                } else null
                CollationLocaleOptions.apply(loc,sink,rbc)
            }
            return coll
        }
        @JvmStatic fun registerInstance(collator: Collator?, locale: ULocale?): Any = CollatorService.registerInstance(collator,locale)
        @JvmStatic fun registerFactory(factory: CollatorFactory?): Any = CollatorService.registerFactory(factory)
        @JvmStatic fun unregister(registryKey: Any?): Boolean = CollatorService.unregister(registryKey)
        @JvmStatic fun getAvailableULocales(): Array<ULocale> = CollatorService.getAvailableULocales()
        @JvmStatic fun getDisplayName(objectLocale: ULocale?, displayLocale: ULocale?): String? = CollatorService.getDisplayName(objectLocale,displayLocale)
        @JvmStatic fun getDisplayName(objectLocale: ULocale?): String? = getDisplayName(objectLocale,ULocale.getDefault(ULocale.Category.DISPLAY))
        private val KEYWORDS = arrayOf("collation")
        @JvmStatic fun getKeywords(): Array<String> = KEYWORDS
        @JvmStatic fun getKeywordValues(keyword: String?): Array<String> {
            if (!keyword!!.equals(KEYWORDS[0])) throw IllegalArgumentException("Invalid keyword: $keyword")
            return ICUResourceBundle.getKeywordValues(ICUData.ICU_COLLATION_BASE_NAME,"collations")
        }
        @JvmStatic fun getKeywordValuesForLocale(key: String?, locale: ULocale?, commonlyUsed: Boolean): Array<String> {
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_COLLATION_BASE_NAME,locale) as ICUResourceBundle
            val sink = KeywordsSink()
            bundle.getAllItemsWithFallback("collations",sink)
            return sink.values.toTypedArray()
        }
        @JvmStatic fun getFunctionalEquivalent(keyword: String?, locID: ULocale?, isAvailable: BooleanArray?): ULocale =
            ICUResourceBundle.getFunctionalEquivalent(ICUData.ICU_COLLATION_BASE_NAME,defaultICUResourceLoader(),"collations",keyword,locID!!,isAvailable,true)
        @JvmStatic fun getFunctionalEquivalent(keyword: String?, locID: ULocale?): ULocale = getFunctionalEquivalent(keyword,locID,null)
        private class KeywordsSink : UResource.Sink() {
            val values = mutableListOf<String>()
            private var hasDefault = false
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                val collations = value.getTable()
                var i = 0
                while (collations.getKeyAndValue(i++,key,value)) {
                    val type = value.getType()
                    if (type == UResourceBundle.STRING) {
                        if (!hasDefault && key.contentEquals("default")) {
                            val default = value.getString()
                            if (default.isNotEmpty()) { values.remove(default); values.add(0,default); hasDefault = true }
                        }
                    } else if (type == UResourceBundle.TABLE && !key.startsWith("private-")) {
                        val name = key.toString()
                        if (!values.contains(name)) values.add(name)
                    }
                }
            }
        }
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
