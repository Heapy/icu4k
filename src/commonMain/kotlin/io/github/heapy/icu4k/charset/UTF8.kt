// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

internal object UTF8 {
    var MAX_LENGTH = 4
    fun countTrailBytes(lead: Byte): Int = when {
        lead < 0xe0.toByte() -> if (lead < 0xc2.toByte()) 0 else 1
        lead < 0xf0.toByte() -> 2
        else -> if (lead <= 0xf4.toByte()) 3 else 0
    }
    fun countBytes(lead: Byte): Int = if (lead >= 0) 1 else when {
        lead < 0xe0.toByte() -> if (lead < 0xc2.toByte()) 0 else 2
        lead < 0xf0.toByte() -> 3
        else -> if (lead <= 0xf4.toByte()) 4 else 0
    }
    private val lead3 = intArrayOf(0x20, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x10, 0x30, 0x30)
    private val lead4 = intArrayOf(0xe00, 0xf00, 0xf00, 0xf00, 0x100)
    fun isValidLead3AndT1(lead: Int, trail: Byte): Boolean = (lead3[lead and 15] and (1 shl ((trail.toInt() and 255) shr 5))) != 0
    fun isValidLead4AndT1(lead: Int, trail: Byte): Boolean {
        val index = lead and 7
        if (index !in lead4.indices) throw IndexOutOfBoundsException()
        return (lead4[index] and (1 shl ((trail.toInt() and 255) shr 4))) != 0
    }
    fun isSingle(c: Byte): Boolean = c >= 0
    fun isLead(c: Byte): Boolean = ((c.toInt() - 0xc2) and 255) <= 0x32
    fun isTrail(c: Byte): Boolean = c < 0xc0.toByte()
    fun length(c: Int): Int = when {
        c < 0 -> 0
        c <= 0x7f -> 1
        c <= 0x7ff -> 2
        c <= 0xd7ff -> 3
        c <= 0xffff -> if (c >= 0xe000) 3 else 0
        c <= 0x10ffff -> 4
        else -> 0
    }
    fun isValidTrail(previous: Int, trail: Byte, index: Int, length: Int): Boolean = when {
        length <= 2 || index > 1 -> isTrail(trail)
        length == 3 -> isValidLead3AndT1(previous, trail)
        else -> isValidLead4AndT1(previous, trail)
    }
}
