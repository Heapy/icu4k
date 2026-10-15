// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.text

import io.github.heapy.icu4k.impl.ICUDebug
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale

/** Legacy primary-weight scanner used by lenient RBNF parsing. */
@Deprecated("This API is ICU internal only.")
open class RbnfScannerProviderImpl : RbnfLenientScannerProvider {
    private val cache = HashMap<String,RbnfLenientScanner?>()
    private val cacheLock = createAvailabilityLock()
    override fun get(locale: ULocale?, extras: String?): RbnfLenientScanner? {
        val key = locale!!.toString() + "/" + extras
        val cached = cacheLock.withLock { cache[key] }
        if (cached != null) return cached
        val result = createScanner(locale,extras)
        cacheLock.withLock { cache[key] = result }
        return result
    }
    protected open fun createScanner(locale: ULocale?, extras: String?): RbnfLenientScanner? {
        var collator: RuleBasedCollator? = null
        try {
            collator = Collator.getInstance(rbnfScannerLocale(locale)) as RuleBasedCollator
            if (extras != null) collator = RuleBasedCollator(collator.getRules() + extras)
            collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION)
        } catch (e: Exception) {
            if (DEBUG) { e.printStackTrace(); println("++++") }
            collator = null
        }
        return RbnfLenientScannerImpl(collator)
    }
    private class RbnfLenientScannerImpl(private val collator: RuleBasedCollator?) : RbnfLenientScanner {
        override fun allIgnorable(s: String?): Boolean {
            val iter = collator!!.getCollationElementIterator(s)
            var o = iter.next()
            while (o != CollationElementIterator.NULLORDER && CollationElementIterator.primaryOrder(o) == 0) o = iter.next()
            return o == CollationElementIterator.NULLORDER
        }
        override fun findText(str: String?, key: String?, startingAt: Int): IntArray {
            var p = startingAt
            var keyLen = 0
            while (p < str!!.length && keyLen == 0) {
                if (p < 0) throw IndexOutOfBoundsException()
                keyLen = prefixLength(str.substring(p),key)
                if (keyLen != 0) return intArrayOf(p,keyLen)
                ++p
            }
            return intArrayOf(-1,0)
        }
        @Suppress("unused")
        fun findText2(str: String?, key: String?, startingAt: Int): IntArray {
            val strIter = collator!!.getCollationElementIterator(str)
            val keyIter = collator.getCollationElementIterator(key)
            var keyStart = -1
            strIter.setOffset(startingAt)
            var oStr = strIter.next()
            var oKey = keyIter.next()
            while (oKey != CollationElementIterator.NULLORDER) {
                while (oStr != CollationElementIterator.NULLORDER && CollationElementIterator.primaryOrder(oStr) == 0) oStr = strIter.next()
                while (oKey != CollationElementIterator.NULLORDER && CollationElementIterator.primaryOrder(oKey) == 0) oKey = keyIter.next()
                if (oStr == CollationElementIterator.NULLORDER) return intArrayOf(-1,0)
                if (oKey == CollationElementIterator.NULLORDER) break
                if (CollationElementIterator.primaryOrder(oStr) == CollationElementIterator.primaryOrder(oKey)) {
                    keyStart = strIter.getOffset(); oStr = strIter.next(); oKey = keyIter.next()
                } else if (keyStart != -1) { keyStart = -1; keyIter.reset() }
                else oStr = strIter.next()
            }
            return intArrayOf(keyStart,strIter.getOffset()-keyStart)
        }
        override fun prefixLength(str: String?, prefix: String?): Int {
            val strIter = collator!!.getCollationElementIterator(str)
            val prefixIter = collator.getCollationElementIterator(prefix)
            var oStr = strIter.next()
            var oPrefix = prefixIter.next()
            while (oPrefix != CollationElementIterator.NULLORDER) {
                while (CollationElementIterator.primaryOrder(oStr) == 0 && oStr != CollationElementIterator.NULLORDER) oStr = strIter.next()
                while (CollationElementIterator.primaryOrder(oPrefix) == 0 && oPrefix != CollationElementIterator.NULLORDER) oPrefix = prefixIter.next()
                if (oPrefix == CollationElementIterator.NULLORDER) break
                if (oStr == CollationElementIterator.NULLORDER) return 0
                if (CollationElementIterator.primaryOrder(oStr) != CollationElementIterator.primaryOrder(oPrefix)) return 0
                oStr = strIter.next(); oPrefix = prefixIter.next()
            }
            var result = strIter.getOffset()
            if (oStr != CollationElementIterator.NULLORDER) --result
            return result
        }
    }
    private companion object { val DEBUG = ICUDebug.enabled("rbnf") }
}
internal expect fun rbnfScannerLocale(locale: ULocale?): ULocale?
/** Language-tag round trip models Locale's supported extension filtering on portable targets. */
internal fun portableRbnfScannerLocale(locale: ULocale?): ULocale = ULocale.forLanguageTag(locale!!.toLanguageTag())
