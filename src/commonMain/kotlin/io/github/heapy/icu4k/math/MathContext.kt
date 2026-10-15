// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.math

import io.github.heapy.icu4k.util.ICUSerializable
import kotlin.jvm.JvmField

/** ICU decimal arithmetic context, distinct from java.math.MathContext. */
class MathContext : ICUSerializable {
    @JvmField internal var digits: Int = 0
    @JvmField internal var form: Int = 0
    @JvmField internal var lostDigits: Boolean = false
    @JvmField internal var roundingMode: Int = 0

    constructor(setdigits: Int) : this(setdigits, SCIENTIFIC, false, ROUND_HALF_UP)
    constructor(setdigits: Int, setform: Int) : this(setdigits, setform, false, ROUND_HALF_UP)
    constructor(setdigits: Int, setform: Int, setlostdigits: Boolean) : this(setdigits, setform, setlostdigits, ROUND_HALF_UP)
    constructor(setdigits: Int, setform: Int, setlostdigits: Boolean, setroundingmode: Int) {
        if (setdigits != 9) {
            if (setdigits < 0) throw IllegalArgumentException("Digits too small: $setdigits")
            if (setdigits > 999999999) throw IllegalArgumentException("Digits too large: $setdigits")
        }
        if (setform != SCIENTIFIC && setform != ENGINEERING && setform != PLAIN) throw IllegalArgumentException("Bad form value: $setform")
        if (setroundingmode !in ROUNDS) throw IllegalArgumentException("Bad roundingMode value: $setroundingmode")
        digits = setdigits; form = setform; lostDigits = setlostdigits; roundingMode = setroundingmode
    }
    fun getDigits(): Int = digits
    fun getForm(): Int = form
    fun getLostDigits(): Boolean = lostDigits
    fun getRoundingMode(): Int = roundingMode
    override fun toString(): String {
        val formString = when (form) { SCIENTIFIC -> "SCIENTIFIC"; ENGINEERING -> "ENGINEERING"; else -> "PLAIN" }
        val roundWord = ROUNDWORDS.getOrNull(ROUNDS.indexOf(roundingMode))
        return "digits=$digits form=$formString lostDigits=${if (lostDigits) "1" else "0"} roundingMode=$roundWord"
    }
    companion object {
        const val PLAIN = 0
        const val SCIENTIFIC = 1
        const val ENGINEERING = 2
        const val ROUND_CEILING = 2
        const val ROUND_DOWN = 1
        const val ROUND_FLOOR = 3
        const val ROUND_HALF_DOWN = 5
        const val ROUND_HALF_EVEN = 6
        const val ROUND_HALF_UP = 4
        const val ROUND_UNNECESSARY = 7
        const val ROUND_UP = 0
        private val ROUNDS = intArrayOf(ROUND_HALF_UP, ROUND_UNNECESSARY, ROUND_CEILING, ROUND_DOWN, ROUND_FLOOR, ROUND_HALF_DOWN, ROUND_HALF_EVEN, ROUND_UP)
        private val ROUNDWORDS = arrayOf("ROUND_HALF_UP", "ROUND_UNNECESSARY", "ROUND_CEILING", "ROUND_DOWN", "ROUND_FLOOR", "ROUND_HALF_DOWN", "ROUND_HALF_EVEN", "ROUND_UP")
        @JvmField val DEFAULT = MathContext(9, SCIENTIFIC, false, ROUND_HALF_UP)
        private const val serialVersionUID: Long = 7163376998892515376L
    }
}
