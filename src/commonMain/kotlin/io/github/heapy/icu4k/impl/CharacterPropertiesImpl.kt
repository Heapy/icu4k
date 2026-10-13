// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.concurrent.atomics.AtomicReference

/** Shared property boundary sets. Returned mutable sets must not be modified. */
internal object CharacterPropertiesImpl {
    private val inclusions = AtomicReference<Map<Int, UnicodeSet>>(emptyMap())
    fun clear() { inclusions.store(emptyMap()) }
    private fun cached(index: Int, build: () -> UnicodeSet): UnicodeSet {
        inclusions.load()[index]?.let { return it }
        val created = build()
        while (true) {
            val old = inclusions.load()
            old[index]?.let { return it }
            if (inclusions.compareAndSet(old, old + (index to created))) return created
        }
    }
    private fun getInclusionsForSource(src: Int): UnicodeSet = cached(src) {
        val incl = UnicodeSet()
        when (src) {
            UCharacterProperty.SRC_CHAR -> UCharacterProperty.INSTANCE.addPropertyStarts(incl)
            UCharacterProperty.SRC_PROPSVEC -> UCharacterProperty.INSTANCE.upropsvec_addPropertyStarts(incl)
            UCharacterProperty.SRC_CHAR_AND_PROPSVEC -> {
                UCharacterProperty.INSTANCE.addPropertyStarts(incl)
                UCharacterProperty.INSTANCE.upropsvec_addPropertyStarts(incl)
            }
            UCharacterProperty.SRC_CASE_AND_NORM -> {
                Norm2AllModes.getNFCInstance().impl.addPropertyStarts(incl)
                UCaseProps.INSTANCE.addPropertyStarts(incl)
            }
            UCharacterProperty.SRC_NFC -> Norm2AllModes.getNFCInstance().impl.addPropertyStarts(incl)
            UCharacterProperty.SRC_NFKC -> Norm2AllModes.getNFKCInstance().impl.addPropertyStarts(incl)
            UCharacterProperty.SRC_NFKC_CF -> Norm2AllModes.getNFKC_CFInstance().impl.addPropertyStarts(incl)
            UCharacterProperty.SRC_NFC_CANON_ITER -> Norm2AllModes.getNFCInstance().impl.addCanonIterPropertyStarts(incl)
            UCharacterProperty.SRC_CASE -> UCaseProps.INSTANCE.addPropertyStarts(incl)
            UCharacterProperty.SRC_BIDI -> UBiDiProps.INSTANCE.addPropertyStarts(incl)
            UCharacterProperty.SRC_INPC, UCharacterProperty.SRC_INSC, UCharacterProperty.SRC_VO -> UCharacterProperty.ulayout_addPropertyStarts(src, incl)
            UCharacterProperty.SRC_EMOJI -> EmojiProps.INSTANCE.addPropertyStarts(incl)
            UCharacterProperty.SRC_IDSU -> { incl.add(0x2ffe); incl.add(0x3000) }
            UCharacterProperty.SRC_ID_COMPAT_MATH -> UCharacterProperty.mathCompat_addPropertyStarts(incl)
            UCharacterProperty.SRC_MCM -> UCharacterProperty.mcm_addPropertyStarts(incl)
            UCharacterProperty.SRC_BLOCK -> UCharacterProperty.INSTANCE.ublock_addPropertyStarts(incl)
            else -> throw IllegalStateException("getInclusions(unknown src $src)")
        }
        incl.compact()
    }
    fun getInclusionsForProperty(prop: Int): UnicodeSet {
        if (prop !in UProperty.INT_START until UProperty.INT_LIMIT) return getInclusionsForSource(UCharacterProperty.INSTANCE.getSource(prop))
        return cached(UCharacterProperty.SRC_COUNT + prop - UProperty.INT_START) {
            val incl = getInclusionsForSource(UCharacterProperty.INSTANCE.getSource(prop))
            val result = UnicodeSet(0, 0)
            var prevValue = 0
            for (i in 0 until incl.getRangeCount()) {
                for (c in incl.getRangeStart(i)..incl.getRangeEnd(i)) {
                    val value = UCharacter.getIntPropertyValue(c, prop)
                    if (value != prevValue) { result.add(c); prevValue = value }
                }
            }
            result.compact()
        }
    }
}
