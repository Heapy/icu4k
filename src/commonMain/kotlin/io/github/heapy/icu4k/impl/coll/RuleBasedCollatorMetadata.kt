// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.text.UnicodeSetIterator
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.VersionInfo

/** RuleBasedCollator introspection and locale state; object/type checks belong to its public wrapper. */
internal class RuleBasedCollatorMetadata(
    private val tailoring: () -> CollationTailoring?,
    private val data: () -> CollationData?,
    private val settings: () -> CollationSettings?,
    private var validLocale: ULocale?,
    private val assertionsEnabled: Boolean = collationIteratorClassAssertionsEnabled(RuleBasedCollatorMetadata::class),
) {
    private var actualLocaleIsSameAsValid = false
    fun getRules(): String = tailoring()!!.getRules()
    fun getRules(fullrules: Boolean): String = if (!fullrules) getRules() else CollationLoader.getRootRules() + getRules()
    fun getTailoredSet(): UnicodeSet {
        val tailored = UnicodeSet()
        if (data()!!.base != null) TailoredSet(tailored).forData(data())
        return tailored
    }
    fun getContractionsAndExpansions(contractions: UnicodeSet?, expansions: UnicodeSet?, addPrefixes: Boolean) {
        contractions?.clear()
        expansions?.clear()
        ContractionsAndExpansions(contractions,expansions,null,addPrefixes).forData(data())
    }
    fun internalAddContractions(c: Int, set: UnicodeSet?) { ContractionsAndExpansions(set,null,null,false).forCodePoint(data(),c) }
    /** Called after the outer equals has checked identity and identical runtime types. */
    fun hasSameBehavior(other: RuleBasedCollatorMetadata): Boolean {
        if (!settings()!!.equals(other.settings())) return false
        if (data() === other.data()) return true
        val thisIsRoot = data()!!.base == null
        val otherIsRoot = other.data()!!.base == null
        checkAssertion { !thisIsRoot || !otherIsRoot }
        if (thisIsRoot != otherIsRoot) return false
        val theseRules = getRules()
        val otherRules = other.getRules()
        if ((thisIsRoot || theseRules.isNotEmpty()) && (otherIsRoot || otherRules.isNotEmpty())) {
            if (theseRules == otherRules) return true
        }
        // ICU4J compares tailored sets here, not every CE mapping.
        return getTailoredSet() == other.getTailoredSet()
    }
    fun behaviorHashCode(): Int {
        var h = settings()!!.hashCode()
        if (data()!!.base == null) return h
        val iter = UnicodeSetIterator(getTailoredSet())
        while (iter.next() && iter.codepoint != UnicodeSetIterator.IS_STRING) h = h xor data()!!.getCE32(iter.codepoint)
        return h
    }
    fun initMaxExpansions() {
        val t = tailoring()!!
        t.maxExpansionsLock.withLock {
            if (t.maxExpansions == null) t.maxExpansions = CollationElementIteratorCore.computeMaxExpansions(t.data)
        }
    }
    fun isUnsafe(c: Int): Boolean = data()!!.isUnsafeBackward(c,settings()!!.isNumeric())
    fun getVersion(): VersionInfo {
        val version = tailoring()!!.version
        val runtime = VersionInfo.UCOL_RUNTIME_VERSION.major
        val major = (version ushr 24) + (runtime shl 4) + (runtime shr 4)
        if (major !in 0..255) throw IllegalArgumentException("Invalid version number: Version number may be negative or greater than 255")
        return VersionInfo.getInstance(major,(version shr 16) and 0xff,(version shr 8) and 0xff,version and 0xff)
    }
    fun getUCAVersion(): VersionInfo {
        val v = getVersion()
        return VersionInfo.getInstance(v.minor shr 3,v.minor and 7,v.milli shr 6,0)
    }
    fun getLocale(type: ULocale.Type?): ULocale? = when (type) {
        ULocale.ACTUAL_LOCALE -> if (actualLocaleIsSameAsValid) validLocale else tailoring()!!.actualLocale
        ULocale.VALID_LOCALE -> validLocale
        else -> throw IllegalArgumentException("unknown ULocale.Type $type")
    }
    fun setLocale(valid: ULocale?, actual: ULocale?) {
        checkAssertion { (valid == null) == (actual == null) }
        if (actual == tailoring()!!.actualLocale) actualLocaleIsSameAsValid = false
        else {
            checkAssertion { actual == valid }
            actualLocaleIsSameAsValid = true
        }
        validLocale = valid
    }
    /** Shallow-clone metadata without invoking setLocale's validation or changing shared tailoring. */
    fun copyLocaleFrom(other: RuleBasedCollatorMetadata) {
        validLocale = other.validLocale
        actualLocaleIsSameAsValid = other.actualLocaleIsSameAsValid
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
}
