// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

/** BCD rounding state machine from DecimalQuantity_AbstractBCD. Accurate Double loading is supplied by its caller. */
internal object DecimalBcdRounding {
    private const val LOWER_EDGE = -1
    private const val UPPER_EDGE = -2
    private const val LOWER = DecimalRoundingDirection.SECTION_LOWER
    private const val MIDPOINT = DecimalRoundingDirection.SECTION_MIDPOINT
    private const val UPPER = DecimalRoundingDirection.SECTION_UPPER
    fun safeSubtract(a: Int, b: Int): Int {
        val difference = a - b
        if (b < 0 && difference < a) return Int.MAX_VALUE
        if (b > 0 && difference > a) return Int.MIN_VALUE
        return difference
    }
    fun round(storage: DecimalBcdStorage, negative: Boolean, magnitude: Int, contextPrecision: Int, mode: Int, nickel: Boolean, reference: Any?, accurate: () -> Unit) {
        var position = safeSubtract(magnitude, storage.scale)
        if (contextPrecision > 0 && storage.precision - contextPrecision > position) position = storage.precision - contextPrecision
        var trailing = storage.getDigitPos(position).toInt()
        if (position <= 0 && !storage.isApproximate && (!nickel || trailing == 0 || trailing == 5)) return
        if (storage.precision == 0) return
        val leading = storage.getDigitPos(safeSubtract(position, 1)).toInt()
        var section: Int
        if (!storage.isApproximate) {
            section = when {
                nickel && trailing != 2 && trailing != 7 -> nickelSection(trailing)
                leading < 5 -> LOWER
                leading > 5 -> UPPER
                else -> {
                    var value = MIDPOINT
                    for (p in safeSubtract(position, 2) downTo 0) if (storage.getDigitPos(p) != 0.toByte()) { value = UPPER; break }
                    value
                }
            }
        } else {
            val minimum = maxOf(0, storage.precision - 14)
            val from = safeSubtract(position, 2)
            fun scan(initial: Int, expected: Int, different: Int): Int {
                for (p in from downTo minimum) if (storage.getDigitPos(p).toInt() != expected) return different
                return initial
            }
            section = when {
                leading == 0 && (!nickel || trailing == 0 || trailing == 5) -> scan(LOWER_EDGE, 0, LOWER)
                leading == 4 && (!nickel || trailing == 2 || trailing == 7) -> scan(MIDPOINT, 9, LOWER)
                leading == 5 && (!nickel || trailing == 2 || trailing == 7) -> scan(MIDPOINT, 0, UPPER)
                leading == 9 && (!nickel || trailing == 4 || trailing == 9) -> scan(UPPER_EDGE, 9, UPPER)
                nickel && trailing != 2 && trailing != 7 -> nickelSection(trailing)
                leading < 5 -> LOWER
                else -> UPPER
            }
            val midpoint = DecimalRoundingDirection.roundsAtMidpoint(mode)
            if (safeSubtract(position, 1) < storage.precision - 14 || midpoint && section == MIDPOINT || !midpoint && section < 0) {
                accurate()
                round(storage, negative, magnitude, contextPrecision, mode, nickel, reference, accurate)
                return
            }
            storage.isApproximate = false; storage.origDouble = 0.0; storage.origDelta = 0
            if (position <= 0 && (!nickel || trailing == 0 || trailing == 5)) return
            if (section == LOWER_EDGE) section = LOWER
            if (section == UPPER_EDGE) section = UPPER
        }
        val even = if (nickel) trailing < 2 || trailing > 7 || trailing == 2 && section != UPPER || trailing == 7 && section == UPPER else trailing % 2 == 0
        val down = DecimalRoundingDirection.getRoundingDirection(even, negative, section, mode, reference)
        if (position >= storage.precision) {
            assertDecimalBcd(trailing == 0)
            storage.setBcdToZero(); storage.scale = magnitude
        } else storage.shiftRight(position)
        if (nickel) {
            if (trailing < 5 && down) { storage.setDigitPos(0, 0); storage.compact(); return }
            else if (trailing >= 5 && !down) { storage.setDigitPos(0, 9); trailing = 9 }
            else { storage.setDigitPos(0, 5); if (storage.precision == 0) storage.precision = 1; return }
        }
        if (!down) {
            if (trailing == 9) {
                var bubble = 0
                while (storage.getDigitPos(bubble) == 9.toByte()) bubble++
                storage.shiftRight(bubble)
            }
            val digit = storage.getDigitPos(0)
            assertDecimalBcd(digit != 9.toByte())
            storage.setDigitPos(0, (digit + 1).toByte()); storage.precision++
        }
        storage.compact()
    }
    private fun nickelSection(trailing: Int): Int = when { trailing < 2 -> LOWER; trailing < 5 -> UPPER; trailing < 7 -> LOWER; else -> UPPER }
}
