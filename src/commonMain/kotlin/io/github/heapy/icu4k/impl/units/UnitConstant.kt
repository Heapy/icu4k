// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.lang.UCharacter

/** Exact scale/range validation for the parser's restricted BigDecimal-to-Long operation. */
internal fun parseUnitConstantPortable(value: String): Long {
    fun invalid(): Nothing = throw IllegalArgumentException("The unit constant value is not a valid non-negative long integer.")
    fun malformed(): Nothing = throw NumberFormatException("Invalid decimal: $value")
    var i = 0; var negative = false
    if (value.isEmpty()) malformed()
    if (value[i] == '+' || value[i] == '-') { negative = value[i] == '-'; i++ }
    val coefficient = StringBuilder(); var fraction = 0; var dot = false; var digitSeen = false
    while (i < value.length && value[i] != 'e' && value[i] != 'E') {
        val ch = value[i++]
        if (ch == '.') { if (dot) malformed(); dot = true; continue }
        val digit = UCharacter.digit(ch.code, 10)
        if (digit < 0) malformed()
        coefficient.append(('0'.code + digit).toChar()); digitSeen = true
        if (dot) fraction++
    }
    if (!digitSeen) malformed()
    var exponent = 0L
    if (i < value.length) {
        i++; var expNegative = false
        if (i < value.length && (value[i] == '+' || value[i] == '-')) { expNegative = value[i] == '-'; i++ }
        if (i == value.length) malformed()
        var exp = 0L
        while (i < value.length) {
            val digit = UCharacter.digit(value[i++].code, 10)
            if (digit < 0) malformed()
            exp = exp * 10 + digit
            if (exp > 4294967295L) malformed()
        }
        val signed = if (expNegative) -exp else exp
        exponent = signed
    }
    val scale = fraction.toLong() - exponent
    if (scale !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) malformed()
    if (scale > 0) invalid()
    val digits = coefficient.toString().trimStart('0')
    if (digits.isEmpty()) return 0
    if (negative || digits.length.toLong() - scale > 19) invalid()
    return (digits + "0".repeat((-scale).toInt())).toLongOrNull() ?: invalid()
}
