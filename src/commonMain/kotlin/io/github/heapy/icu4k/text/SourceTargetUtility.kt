// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.CharSequences

/** Eager affected-character/decomposition cache used by casing and normalization. */
internal class SourceTargetUtility(val transform: Transform<String, out String?>, normalizer: Normalizer2? = null) {
    val sourceCache: UnicodeSet = if (normalizer != null) UnicodeSet("[:^ccc=0:]") else UnicodeSet()
    val sourceStrings: Set<String>
    init {
        val strings = HashSet<String>()
        for (c in 0..0x10ffff) {
            var result = transform.transform(UTF16.valueOf(c))
            val added = !CharSequences.equals(c, result)
            if (added) sourceCache.add(c)
            if (normalizer == null) continue
            val decomposition = NFC.getDecomposition(c) ?: continue
            result = transform.transform(decomposition)
            if (decomposition != result) strings.add(decomposition)
            if (!added && !normalizer.isInert(c)) sourceCache.add(c)
        }
        sourceCache.freeze()
        sourceStrings = strings
    }
    fun addSourceTargetSet(filterAsSet: (UnicodeSet) -> UnicodeSet, inputFilter: UnicodeSet, sourceSet: UnicodeSet, targetSet: UnicodeSet) {
        val filter = filterAsSet(inputFilter)
        val affected = UnicodeSet(sourceCache).retainAll(filter)
        sourceSet.addAll(affected)
        for (s in affected) targetSet.addAll(transform.transform(s)!!)
        for (s in sourceStrings) {
            if (filter.containsAll(s)) {
                val result = transform.transform(s)
                if (s != result) { targetSet.addAll(result!!); sourceSet.addAll(s) }
            }
        }
    }
    companion object {
        val NON_STARTERS: UnicodeSet = UnicodeSet("[:^ccc=0:]").freeze()
        var NFC: Normalizer2 = Normalizer2.getNFCInstance()
    }
}
