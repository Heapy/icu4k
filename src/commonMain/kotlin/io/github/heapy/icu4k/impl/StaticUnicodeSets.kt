// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Frozen CLDR parse-symbol, separator, digit and formatting-trim sets. */
object StaticUnicodeSets {
    enum class Key {
        EMPTY, DEFAULT_IGNORABLES, STRICT_IGNORABLES,
        COMMA, PERIOD, STRICT_COMMA, STRICT_PERIOD, APOSTROPHE_SIGN, OTHER_GROUPING_SEPARATORS,
        ALL_SEPARATORS, STRICT_ALL_SEPARATORS,
        MINUS_SIGN, PLUS_SIGN, PERCENT_SIGN, PERMILLE_SIGN, INFINITY_SIGN, APPROXIMATELY_SIGN,
        DOLLAR_SIGN, POUND_SIGN, RUPEE_SIGN, YEN_SIGN, WON_SIGN,
        DIGITS, DIGITS_OR_ALL_SEPARATORS, DIGITS_OR_STRICT_ALL_SEPARATORS
    }
    private val sets = HashMap<Key, UnicodeSet>()
    fun get(key: Key?): UnicodeSet = sets[key] ?: UnicodeSet.EMPTY
    fun chooseFrom(str: String?, key: Key?): Key? = if (get(key).contains(str!!)) key else null
    fun chooseFrom(str: String?, first: Key?, second: Key?): Key? = if (get(first).contains(str!!)) first else chooseFrom(str, second)
    fun chooseCurrency(str: String?): Key? {
        for (key in arrayOf(Key.DOLLAR_SIGN, Key.POUND_SIGN, Key.RUPEE_SIGN, Key.YEN_SIGN, Key.WON_SIGN))
            if (get(key).contains(str!!)) return key
        return null
    }
    private fun union(vararg keys: Key): UnicodeSet = UnicodeSet().also { result -> keys.forEach { result.addAll(get(it)) } }.freeze()
    internal class ParseDataSink(private val destination: MutableMap<Key, UnicodeSet>) : UResource.Sink() {
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val contexts = value.getTable()
            var i = 0
            while (contexts.getKeyAndValue(i++, key, value)) {
                if (key.contentEquals("date")) continue
                val strictness = value.getTable()
                var j = 0
                while (strictness.getKeyAndValue(j++, key, value)) {
                    val lenient = key.contentEquals("lenient")
                    val array = value.getArray()
                    for (index in 0 until array.getSize()) {
                        array.getValue(index, value)
                        val pattern = value.toString()
                        val kind = when {
                            '.' in pattern -> if (lenient) Key.PERIOD else Key.STRICT_PERIOD
                            ',' in pattern -> if (lenient) Key.COMMA else Key.STRICT_COMMA
                            '+' in pattern -> Key.PLUS_SIGN
                            '-' in pattern -> Key.MINUS_SIGN
                            '$' in pattern -> Key.DOLLAR_SIGN
                            '£' in pattern -> Key.POUND_SIGN
                            '₹' in pattern -> Key.RUPEE_SIGN
                            '¥' in pattern -> Key.YEN_SIGN
                            '₩' in pattern -> Key.WON_SIGN
                            '%' in pattern -> Key.PERCENT_SIGN
                            '‰' in pattern -> Key.PERMILLE_SIGN
                            '’' in pattern -> Key.APOSTROPHE_SIGN
                            else -> throw AssertionError("Unknown class of parse lenients: $pattern")
                        }
                        destination[kind] = UnicodeSet(pattern).freeze()
                    }
                }
            }
        }
    }
    init {
        sets[Key.EMPTY] = UnicodeSet("[]").freeze()
        sets[Key.DEFAULT_IGNORABLES] = UnicodeSet("[[:Zs:][\\u0009][:Bidi_Control:][:Variation_Selector:]]").freeze()
        sets[Key.STRICT_IGNORABLES] = UnicodeSet("[[:Bidi_Control:]]").freeze()
        val root = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, ULocale.ROOT) as ICUResourceBundle
        root.getAllItemsWithFallback("parse", ParseDataSink(sets))
        val other = UnicodeSet("[٬‘\\u0020\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]")
        other.addAll(sets[Key.APOSTROPHE_SIGN]!!)
        sets[Key.OTHER_GROUPING_SEPARATORS] = other.freeze()
        sets[Key.ALL_SEPARATORS] = union(Key.COMMA, Key.PERIOD, Key.OTHER_GROUPING_SEPARATORS)
        sets[Key.STRICT_ALL_SEPARATORS] = union(Key.STRICT_COMMA, Key.STRICT_PERIOD, Key.OTHER_GROUPING_SEPARATORS)
        sets[Key.INFINITY_SIGN] = UnicodeSet("[∞]").freeze()
        sets[Key.APPROXIMATELY_SIGN] = UnicodeSet("[∼~≈≃約]").freeze()
        sets[Key.DIGITS] = UnicodeSet("[:digit:]").freeze()
        sets[Key.DIGITS_OR_ALL_SEPARATORS] = union(Key.DIGITS, Key.ALL_SEPARATORS)
        sets[Key.DIGITS_OR_STRICT_ALL_SEPARATORS] = union(Key.DIGITS, Key.STRICT_ALL_SEPARATORS)
    }
}
