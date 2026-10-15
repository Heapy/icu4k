// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.number.Modifier.Signum

open class AdoptingModifierStore : ModifierStore {
    private val positive: Modifier?
    private val posZero: Modifier?
    private val negZero: Modifier?
    private val negative: Modifier?
    internal val mods: Array<Modifier?>?
    internal var frozen: Boolean
    constructor(positive: Modifier?, posZero: Modifier?, negZero: Modifier?, negative: Modifier?) {
        this.positive = positive; this.posZero = posZero; this.negZero = negZero; this.negative = negative
        mods = null; frozen = true
    }
    constructor() {
        positive = null; posZero = null; negZero = null; negative = null
        mods = arrayOfNulls(Signum.COUNT * StandardPlural.COUNT); frozen = false
    }
    open fun setModifier(signum: Signum?, plural: StandardPlural?, mod: Modifier?) {
        assertAdoptingModifierStore(!frozen)
        mods!![getModIndex(signum, plural)] = mod
    }
    open fun freeze() { frozen = true }
    open fun getModifierWithoutPlural(signum: Signum?): Modifier? {
        assertAdoptingModifierStore(frozen)
        assertAdoptingModifierStore(mods == null)
        assertAdoptingModifierStore(signum != null)
        return when (signum!!) {
            Signum.POS -> positive
            Signum.POS_ZERO -> posZero
            Signum.NEG_ZERO -> negZero
            Signum.NEG -> negative
        }
    }
    override fun getModifier(signum: Signum?, plural: StandardPlural?): Modifier? {
        assertAdoptingModifierStore(frozen)
        assertAdoptingModifierStore(positive == null)
        return mods!![getModIndex(signum, plural)]
    }
    private fun getModIndex(signum: Signum?, plural: StandardPlural?): Int {
        assertAdoptingModifierStore(signum != null)
        assertAdoptingModifierStore(plural != null)
        return plural!!.ordinal * Signum.COUNT + signum!!.ordinal
    }
}

internal expect fun assertAdoptingModifierStore(condition: Boolean)
