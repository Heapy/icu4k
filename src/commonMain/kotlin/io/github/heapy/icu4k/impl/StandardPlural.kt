// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.localeReadOnlyList

enum class StandardPlural(private val keyword: String) {
    ZERO("zero"), ONE("one"), TWO("two"), FEW("few"), MANY("many"), OTHER("other"), EQ_0("=0"), EQ_1("=1");
    fun getKeyword(): String = keyword
    companion object {
        val OTHER_INDEX = OTHER.ordinal
        val VALUES: List<StandardPlural> = localeReadOnlyList(entries.toList())
        val COUNT = VALUES.size
        fun orNullFromString(keyword: CharSequence?): StandardPlural? {
            val actual = keyword!!
            return when (actual.length) {
                1 -> if (actual[0] == '0') EQ_0 else if (actual[0] == '1') EQ_1 else null
                2 -> when { "=0".contentEquals(actual) -> EQ_0; "=1".contentEquals(actual) -> EQ_1; else -> null }
                3 -> when { "one".contentEquals(actual) -> ONE; "two".contentEquals(actual) -> TWO; "few".contentEquals(actual) -> FEW; else -> null }
                4 -> when { "many".contentEquals(actual) -> MANY; "zero".contentEquals(actual) -> ZERO; else -> null }
                5 -> if ("other".contentEquals(actual)) OTHER else null
                else -> null
            }
        }
        fun orOtherFromString(keyword: CharSequence?): StandardPlural = orNullFromString(keyword) ?: OTHER
        fun fromString(keyword: CharSequence?): StandardPlural = orNullFromString(keyword) ?: throw IllegalArgumentException(keyword.toString())
        fun indexOrNegativeFromString(keyword: CharSequence?): Int = orNullFromString(keyword)?.ordinal ?: -1
        fun indexOrOtherIndexFromString(keyword: CharSequence?): Int = orNullFromString(keyword)?.ordinal ?: OTHER_INDEX
        fun indexFromString(keyword: CharSequence?): Int = fromString(keyword).ordinal
    }
}
