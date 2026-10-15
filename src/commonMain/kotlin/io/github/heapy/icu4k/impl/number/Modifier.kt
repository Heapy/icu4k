// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.text.FormatField
import kotlin.jvm.JvmField

interface Modifier {
    enum class Signum {
        NEG, NEG_ZERO, POS_ZERO, POS;
        companion object {
            internal const val COUNT = 4
            @JvmField val VALUES: Array<Signum> = entries.toTypedArray()
        }
    }
    fun apply(output: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int
    fun getPrefixLength(): Int
    fun getCodePointCount(): Int
    fun isStrong(): Boolean
    fun containsField(field: FormatField?): Boolean
    class Parameters {
        @JvmField var obj: ModifierStore? = null
        @JvmField var signum: Signum? = null
        @JvmField var plural: StandardPlural? = null
    }
    fun getParameters(): Parameters?
    fun strictEquals(other: Modifier?): Boolean
    fun semanticallyEquivalent(other: Modifier?): Boolean {
        val first = getParameters()
        val second = other!!.getParameters()
        if (first == null && second == null) return strictEquals(other)
        if (first == null || second == null) return false
        if (first.obj == null && second.obj == null) return strictEquals(other)
        if (first.obj == null || second.obj == null) return false
        for (signum in Signum.VALUES) for (plural in StandardPlural.VALUES) {
            val a = first.obj!!.getModifier(signum, plural)
            val b = second.obj!!.getModifier(signum, plural)
            if (a === b) continue
            if (a == null || b == null || !a.strictEquals(b)) return false
        }
        return true
    }
}

interface ModifierStore {
    fun getModifier(signum: Modifier.Signum?, plural: StandardPlural?): Modifier?
}

internal fun modifierCodePointCount(chars: CharSequence): Int {
    var count = 0
    var i = 0
    while (i < chars.length) {
        val c = chars[i++]
        if (c.isHighSurrogate() && i < chars.length && chars[i].isLowSurrogate()) i++
        count++
    }
    return count
}

internal fun modifierCodePointCount(chars: CharArray): Int {
    var count = 0
    var i = 0
    while (i < chars.size) {
        val c = chars[i++]
        if (c.isHighSurrogate() && i < chars.size && chars[i].isLowSurrogate()) i++
        count++
    }
    return count
}
