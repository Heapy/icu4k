package io.github.heapy.icu4k.math

import kotlin.test.*

class MathContextTest {
    @Test fun defaultsAndConstructorOverloads() {
        val default = MathContext.DEFAULT
        assertEquals(9, default.getDigits()); assertEquals(MathContext.SCIENTIFIC, default.getForm())
        assertFalse(default.getLostDigits()); assertEquals(MathContext.ROUND_HALF_UP, default.getRoundingMode())
        assertEquals("digits=9 form=SCIENTIFIC lostDigits=0 roundingMode=ROUND_HALF_UP", default.toString())
        assertEquals(default.toString(), MathContext(9).toString())
        assertNotEquals(default, MathContext(9))
        assertEquals("digits=0 form=PLAIN lostDigits=0 roundingMode=ROUND_HALF_UP", MathContext(0, MathContext.PLAIN).toString())
        assertEquals("digits=7 form=ENGINEERING lostDigits=1 roundingMode=ROUND_HALF_UP", MathContext(7, MathContext.ENGINEERING, true).toString())
        assertEquals("digits=999999999 form=PLAIN lostDigits=1 roundingMode=ROUND_UNNECESSARY", MathContext(999999999, MathContext.PLAIN, true, MathContext.ROUND_UNNECESSARY).toString())
    }
    @Test fun validationOrderAndDiagnostics() {
        data class Invalid(val digits: Int, val form: Int, val round: Int, val message: String)
        for ((digits, form, round, message) in listOf(
            Invalid(-1, -1, -1, "Digits too small: -1"),
            Invalid(1000000000, -1, -1, "Digits too large: 1000000000"),
            Invalid(9, -1, -1, "Bad form value: -1"),
            Invalid(9, 0, -1, "Bad roundingMode value: -1")
        )) {
            val failure = assertFailsWith<IllegalArgumentException> { MathContext(digits, form, false, round) }
            assertEquals(message, failure.message)
        }
    }
}
