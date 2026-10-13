// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.impl.CharacterPropertiesImpl
import io.github.heapy.icu4k.impl.EmojiProps
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.CodePointMap
import io.github.heapy.icu4k.util.CodePointTrie
import io.github.heapy.icu4k.util.MutableCodePointTrie
import kotlin.concurrent.atomics.AtomicReference

/** Immutable sets and maps for complete Unicode properties. */
class CharacterProperties private constructor() {
    companion object {
        private val sets = AtomicReference<Map<Int, UnicodeSet>>(emptyMap())
        private val maps = AtomicReference<Map<Int, CodePointMap>>(emptyMap())
        private fun makeSet(property: Int): UnicodeSet {
            val set = UnicodeSet()
            if (property in UProperty.BASIC_EMOJI..UProperty.RGI_EMOJI) {
                EmojiProps.INSTANCE.addStrings(property, set)
                if (property != UProperty.BASIC_EMOJI && property != UProperty.RGI_EMOJI) return set.freeze()
            }
            val inclusions = CharacterPropertiesImpl.getInclusionsForProperty(property)
            var start = -1
            for (i in 0 until inclusions.getRangeCount()) {
                for (c in inclusions.getRangeStart(i)..inclusions.getRangeEnd(i)) {
                    if (UCharacter.hasBinaryProperty(c, property)) {
                        if (start < 0) start = c
                    } else if (start >= 0) {
                        set.add(start, c - 1)
                        start = -1
                    }
                }
            }
            if (start >= 0) set.add(start, 0x10ffff)
            return set.freeze()
        }
        private fun makeMap(property: Int): CodePointMap {
            val nullValue = if (property == UProperty.SCRIPT) UScript.UNKNOWN else 0
            val trie = MutableCodePointTrie(nullValue, nullValue)
            val inclusions = CharacterPropertiesImpl.getInclusionsForProperty(property)
            var start = 0
            var value = nullValue
            for (i in 0 until inclusions.getRangeCount()) {
                for (c in inclusions.getRangeStart(i)..inclusions.getRangeEnd(i)) {
                    val next = UCharacter.getIntPropertyValue(c, property)
                    if (value != next) {
                        if (value != nullValue) trie.setRange(start, c - 1, value)
                        start = c
                        value = next
                    }
                }
            }
            if (value != 0) trie.setRange(start, 0x10ffff, value)
            val type = if (property == UProperty.BIDI_CLASS || property == UProperty.GENERAL_CATEGORY) CodePointTrie.Type.FAST else CodePointTrie.Type.SMALL
            val max = UCharacter.getIntPropertyMaxValue(property)
            val width = when { max <= 255 -> CodePointTrie.ValueWidth.BITS_8; max <= 65535 -> CodePointTrie.ValueWidth.BITS_16; else -> CodePointTrie.ValueWidth.BITS_32 }
            return trie.buildImmutable(type, width)
        }
        fun getBinaryPropertySet(property: Int): UnicodeSet {
            require(property in UProperty.BINARY_START until UProperty.BINARY_LIMIT) { "$property is not a constant for a UProperty binary property" }
            sets.load()[property]?.let { return it }
            val created = makeSet(property)
            while (true) {
                val old = sets.load()
                old[property]?.let { return it }
                if (sets.compareAndSet(old, old + (property to created))) return created
            }
        }
        fun getIntPropertyMap(property: Int): CodePointMap {
            require(property in UProperty.INT_START until UProperty.INT_LIMIT) { "$property is not a constant for a UProperty int property" }
            maps.load()[property]?.let { return it }
            val created = makeMap(property)
            while (true) {
                val old = maps.load()
                old[property]?.let { return it }
                if (maps.compareAndSet(old, old + (property to created))) return created
            }
        }
    }
}
