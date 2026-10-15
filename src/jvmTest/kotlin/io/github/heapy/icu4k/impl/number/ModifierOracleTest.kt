@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.FormattedStringBuilder as JBuilder
import com.ibm.icu.impl.StandardPlural as JPlural
import com.ibm.icu.impl.number.Modifier as JModifier
import com.ibm.icu.impl.number.ModifierStore as JStore
import com.ibm.icu.impl.number.ConstantAffixModifier as JAffix
import com.ibm.icu.impl.number.ConstantMultiFieldModifier as JMulti
import com.ibm.icu.impl.number.AdoptingModifierStore as JAdopting
import com.ibm.icu.text.NumberFormat.Field as JField
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.text.NumberFormatField
import kotlin.test.*

class ModifierOracleTest {
    private val jf = listOf(null, JField.INTEGER, JField.CURRENCY, JField.SIGN)
    private val kf = listOf(null, NumberFormatField.INTEGER, NumberFormatField.CURRENCY, NumberFormatField.SIGN)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { e.javaClass.simpleName }
    private fun check(j: JModifier, k: Modifier) {
        assertEquals(j.prefixLength, k.getPrefixLength())
        assertEquals(j.codePointCount, k.getCodePointCount())
        assertEquals(j.isStrong, k.isStrong())
        assertEquals(j.toString(), k.toString())
        assertEquals(outcome { j.containsField(null) }, outcome { k.containsField(null) })
        for (left in 0..4) for (right in left..4) {
            val jb = JBuilder().apply { append("0123", JField.INTEGER) }
            val kb = FormattedStringBuilder().apply { append("0123", NumberFormatField.INTEGER) }
            assertEquals(j.apply(jb, left, right), k.apply(kb, left, right))
            assertEquals(jb.toString(), kb.toString())
            assertEquals(jb.toFieldArray().map { jf.indexOf(it) }, kb.toFieldArray().map { kf.indexOf(it) })
        }
        assertEquals(outcome { j.apply(null, 0, 0) }, outcome { k.apply(null, 0, 0) })
    }
    @Test fun constantAffixesAndMultiFieldCopiesMatchSource() {
        val texts = listOf(null, "", "-", "😀", "\ud800", "\udc00", "日本")
        for (prefix in texts) for (suffix in texts) for (field in jf.indices) for (strong in listOf(false, true)) {
            val j = JAffix(prefix, suffix, jf[field], strong); val k = ConstantAffixModifier(prefix, suffix, kf[field], strong)
            check(j, k)
            assertEquals(j.strictEquals(JAffix(prefix, suffix, jf[field], strong)), k.strictEquals(ConstantAffixModifier(prefix, suffix, kf[field], strong)))
            assertEquals(j.strictEquals(null), k.strictEquals(null))
            assertEquals(j.semanticallyEquivalent(JAffix(prefix, suffix, jf[field], strong)), k.semanticallyEquivalent(ConstantAffixModifier(prefix, suffix, kf[field], strong)))
        }
        for (prefix in texts.drop(1)) for (suffix in texts.drop(1)) for (overwrite in listOf(false, true)) for (strong in listOf(false, true)) {
            fun jb(text: String) = JBuilder().apply { text.forEachIndexed { i, c -> appendChar16(c, jf[i % jf.size]) } }
            fun kb(text: String) = FormattedStringBuilder().apply { text.forEachIndexed { i, c -> appendChar16(c, kf[i % kf.size]) } }
            val jp = jb(prefix!!); val js = jb(suffix!!); val kp = kb(prefix); val ks = kb(suffix)
            val j = JMulti(jp, js, overwrite, strong); val k = ConstantMultiFieldModifier(kp, ks, overwrite, strong)
            jp.clear(); js.clear(); kp.clear(); ks.clear()
            check(j, k)
            for (field in jf.indices) assertEquals(j.containsField(jf[field]), k.containsField(kf[field]))
            assertEquals(j.strictEquals(JMulti(jb(prefix), jb(suffix), overwrite, strong)), k.strictEquals(ConstantMultiFieldModifier(kb(prefix), kb(suffix), overwrite, strong)))
            assertEquals(j.strictEquals(JAffix()), k.strictEquals(ConstantAffixModifier()))
        }
    }
    @Test fun adoptingStoreIndexingNullsAndAssertionsMatchSource() {
        val j = JAdopting(); val k = AdoptingModifierStore()
        assertEquals(outcome { j.getModifier(JModifier.Signum.POS, JPlural.ONE) }, outcome { k.getModifier(Modifier.Signum.POS, StandardPlural.ONE) })
        for (s in JModifier.Signum.entries) for (p in JPlural.VALUES) {
            j.setModifier(s, p, JAffix("$s/$p", "", null, false))
            k.setModifier(Modifier.Signum.valueOf(s.name), StandardPlural.valueOf(p.name), ConstantAffixModifier("$s/$p", "", null, false))
        }
        j.freeze(); k.freeze()
        for (s in JModifier.Signum.entries + listOf(null)) for (p in JPlural.VALUES + listOf(null)) {
            val ks = s?.let { Modifier.Signum.valueOf(it.name) }; val kp = p?.let { StandardPlural.valueOf(it.name) }
            assertEquals(outcome { j.getModifier(s, p)?.toString() }, outcome { k.getModifier(ks, kp)?.toString() }, "$s/$p")
            assertEquals(outcome { j.setModifier(s, p, null) }, outcome { k.setModifier(ks, kp, null) }, "$s/$p/set frozen")
        }
        val jf = JAdopting(JAffix("+", "", null, false), JAffix("0+", "", null, false), null, JAffix("-", "", null, false))
        val kf = AdoptingModifierStore(ConstantAffixModifier("+", "", null, false), ConstantAffixModifier("0+", "", null, false), null, ConstantAffixModifier("-", "", null, false))
        for (s in JModifier.Signum.entries + listOf(null)) {
            assertEquals(outcome { jf.getModifierWithoutPlural(s)?.toString() }, outcome { kf.getModifierWithoutPlural(s?.let { Modifier.Signum.valueOf(it.name) })?.toString() })
        }
    }
    @Test fun semanticEquivalenceChecksCompleteStoresAndIgnoresCurrentParameters() {
        for (modeA in 0..3) for (modeB in 0..3) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            fun jStore(mode: Int) = object : JStore {
                override fun getModifier(signum: JModifier.Signum?, plural: JPlural?): JModifier? {
                    jt.add("$mode/$signum/$plural")
                    return when (mode) { 0 -> null; 1 -> JAffix("+", "", null, false); 2 -> if (plural == JPlural.EQ_1) null else JAffix("+", "", null, false); else -> JAffix("$signum", "", null, false) }
                }
            }
            fun kStore(mode: Int) = object : ModifierStore {
                override fun getModifier(signum: Modifier.Signum?, plural: StandardPlural?): Modifier? {
                    kt.add("$mode/$signum/$plural")
                    return when (mode) { 0 -> null; 1 -> ConstantAffixModifier("+", "", null, false); 2 -> if (plural == StandardPlural.EQ_1) null else ConstantAffixModifier("+", "", null, false); else -> ConstantAffixModifier("$signum", "", null, false) }
                }
            }
            fun jMod(mode: Int, sign: JModifier.Signum): JModifier = JMulti(JBuilder(), JBuilder(), false, false, JModifier.Parameters().apply { obj = jStore(mode); signum = sign; plural = JPlural.ONE })
            fun kMod(mode: Int, sign: Modifier.Signum): Modifier = ConstantMultiFieldModifier(FormattedStringBuilder(), FormattedStringBuilder(), false, false, Modifier.Parameters().apply { obj = kStore(mode); signum = sign; plural = StandardPlural.ONE })
            val j = jMod(modeA, JModifier.Signum.NEG); val jo = jMod(modeB, JModifier.Signum.POS)
            val k = kMod(modeA, Modifier.Signum.NEG); val ko = kMod(modeB, Modifier.Signum.POS)
            assertEquals(j.strictEquals(jo), k.strictEquals(ko))
            assertEquals(j.semanticallyEquivalent(jo), k.semanticallyEquivalent(ko), "$modeA/$modeB")
            assertEquals(jt, kt, "$modeA/$modeB/trace")
            assertEquals(outcome { j.semanticallyEquivalent(null) }, outcome { k.semanticallyEquivalent(null) })
        }
        val jp = JModifier.Parameters(); val kp = Modifier.Parameters()
        val j = JMulti(JBuilder(), JBuilder(), false, false, jp)
        val k = ConstantMultiFieldModifier(FormattedStringBuilder(), FormattedStringBuilder(), false, false, kp)
        assertEquals(j.semanticallyEquivalent(JMulti(JBuilder(), JBuilder(), false, false, jp)), k.semanticallyEquivalent(ConstantMultiFieldModifier(FormattedStringBuilder(), FormattedStringBuilder(), false, false, kp)))
        assertEquals(j.semanticallyEquivalent(JMulti(JBuilder(), JBuilder(), false, false)), k.semanticallyEquivalent(ConstantMultiFieldModifier(FormattedStringBuilder(), FormattedStringBuilder(), false, false)))
    }
}
