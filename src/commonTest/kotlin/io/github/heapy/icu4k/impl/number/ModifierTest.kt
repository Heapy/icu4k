// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.text.NumberFormatField
import kotlin.test.*

class ModifierTest {
    @Test fun affixesCountCodePointsAndPreserveFields() {
        val output = FormattedStringBuilder().apply { append("12", NumberFormatField.INTEGER) }
        val mod = ConstantAffixModifier("😀-", "%", NumberFormatField.SIGN, true)
        assertEquals(4, mod.apply(output, 0, 2))
        assertEquals("😀-12%", output.toString())
        assertEquals(3, mod.getCodePointCount())
        assertEquals(3, mod.getPrefixLength())
        assertSame(NumberFormatField.INTEGER, output.fieldAt(3))
        assertSame(NumberFormatField.SIGN, output.fieldAt(0))
        assertTrue(mod.isStrong())
        assertEquals(2, ConstantAffixModifier("\ud800", "\udc00", null, false).getCodePointCount())
        assertTrue(ConstantAffixModifier.EMPTY.strictEquals(ConstantAffixModifier(null, null, null, false)))
    }
    @Test fun multiFieldOverwriteCopiesInputAndCountsDelta() {
        val prefix = FormattedStringBuilder().apply { append("$", NumberFormatField.CURRENCY); append(" ", null) }
        val suffix = FormattedStringBuilder().apply { append("!", NumberFormatField.SIGN) }
        val mod = ConstantMultiFieldModifier(prefix, suffix, true, false)
        prefix.clear(); suffix.clear()
        val output = FormattedStringBuilder().apply { append("[123]", NumberFormatField.INTEGER) }
        assertEquals(0, mod.apply(output, 1, 4))
        assertEquals("[$ !]", output.toString())
        assertTrue(mod.containsField(null))
        assertTrue(mod.containsField(NumberFormatField.CURRENCY))
        assertFalse(mod.containsField(NumberFormatField.PERCENT))
        assertEquals("<ConstantMultiFieldModifier prefix:'$ ' suffix:'!'>", mod.toString())
    }
    @Test fun adoptingStoresSelectAllSignsAndPluralForms() {
        val store = AdoptingModifierStore()
        val forms = mutableMapOf<Pair<Modifier.Signum, StandardPlural>, Modifier>()
        for (sign in Modifier.Signum.entries) for (plural in StandardPlural.VALUES) {
            val mod = ConstantAffixModifier("$sign/$plural:", "", null, false)
            forms[sign to plural] = mod
            store.setModifier(sign, plural, mod)
        }
        store.freeze()
        for ((key, value) in forms) assertSame(value, store.getModifier(key.first, key.second))
        val fixed = AdoptingModifierStore(ConstantAffixModifier.EMPTY, null, null, null)
        assertSame(ConstantAffixModifier.EMPTY, fixed.getModifierWithoutPlural(Modifier.Signum.POS))
        assertNull(fixed.getModifierWithoutPlural(Modifier.Signum.NEG_ZERO))
        val params = Modifier.Parameters().apply { obj = store }
        val empty = FormattedStringBuilder()
        val a = ConstantMultiFieldModifier(empty, empty, false, false, params)
        val b = ConstantMultiFieldModifier(empty, empty, false, false, params)
        assertTrue(a.semanticallyEquivalent(b))
        assertFalse(a.semanticallyEquivalent(ConstantMultiFieldModifier(empty, empty, false, false)))
    }
}
