package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.Padder as JPadder
import com.ibm.icu.impl.number.ConstantAffixModifier as JAffix
import com.ibm.icu.impl.FormattedStringBuilder as JBuilder
import com.ibm.icu.text.NumberFormat.Field as JField
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.NumberFormatField
import kotlin.test.*

class PadderOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { if (e is IllegalArgumentException) "argument:${e.message}" else e.javaClass.simpleName }
    @Test fun allPositionsStringsWidthsAndFieldsMatchSource() {
        for (text in listOf("", "12", "😀1")) for (pad in listOf(null, "", "_", "ab", "😀")) for (width in listOf(-1, 0, 1, 4, 8)) for (position in JPadder.PadPosition.entries + listOf(null)) {
            val j = JPadder(pad, width, position); val k = Padder(pad, width, position?.let { Padder.PadPosition.valueOf(it.name) })
            assertEquals(j.isValid, k.isValid())
            val jb = JBuilder().apply { append(text, JField.INTEGER) }; val kb = FormattedStringBuilder().apply { append(text, NumberFormatField.INTEGER) }
            assertEquals(j.padAndApply(JAffix("P", "S", JField.SIGN, false), JAffix("[", "]", JField.CURRENCY, false), jb, 0, jb.length),
                k.padAndApply(ConstantAffixModifier("P", "S", NumberFormatField.SIGN, false), ConstantAffixModifier("[", "]", NumberFormatField.CURRENCY, false), kb, 0, kb.length), "$text/$pad/$width/$position")
            assertEquals(jb.toDebugString(), kb.toDebugString())
        }
        for (old in -2..5) assertEquals(outcome { JPadder.PadPosition.fromOld(old).toOld() }, outcome { Padder.PadPosition.fromOld(old).toOld() })
        for (cp in listOf(-1, 0, 32, 0xd800, 0x1f600, 0x10ffff, 0x110000)) for (width in listOf(-1, 0, 4)) {
            assertEquals(outcome { JPadder.codePoints(cp, width, null).isValid }, outcome { Padder.codePoints(cp, width, null).isValid() }, "$cp/$width")
        }
    }
    @Test fun nullsAndAssertionOnlyRangeCheckMatchSource() {
        val j = JPadder(null, 4, null); val k = Padder(null, 4, null)
        assertEquals(outcome { j.padAndApply(null, null, null, 0, 0) }, outcome { k.padAndApply(null, null, null, 0, 0) })
        val jb = JBuilder().apply { append("123", null) }; val kb = FormattedStringBuilder().apply { append("123", null) }
        assertEquals(outcome { j.padAndApply(JAffix.EMPTY, JAffix.EMPTY, jb, 1, 2) }, outcome { k.padAndApply(ConstantAffixModifier.EMPTY, ConstantAffixModifier.EMPTY, kb, 1, 2) })
        assertEquals(jb.toString(), kb.toString())
    }
}
