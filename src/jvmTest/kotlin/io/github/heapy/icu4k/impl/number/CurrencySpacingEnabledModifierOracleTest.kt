@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.FormattedStringBuilder as JBuilder
import com.ibm.icu.impl.number.CurrencySpacingEnabledModifier as JSpacing
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencySpacingEnabledModifierOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName }
    }
    private fun jBuffer(text: String, mode: Int) = JBuilder().apply {
        for ((i, c) in text.withIndex()) appendChar16(c, when (mode) {
            1 -> JField.CURRENCY
            2 -> if (i == 0 || i == text.lastIndex) JField.CURRENCY else JField.SIGN
            3 -> JField.INTEGER
            else -> null
        })
    }
    private fun kBuffer(text: String, mode: Int) = FormattedStringBuilder().apply {
        for ((i, c) in text.withIndex()) appendChar16(c, when (mode) {
            1 -> NumberFormatField.CURRENCY
            2 -> if (i == 0 || i == text.lastIndex) NumberFormatField.CURRENCY else NumberFormatField.SIGN
            3 -> NumberFormatField.INTEGER
            else -> null
        })
    }
    private fun compare(j: JBuilder, k: FormattedStringBuilder, label: String = "") {
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.toFieldArray().map { it?.toString()?.substringAfterLast('(') },
            k.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, label)
    }
    private fun settings(index: Int): List<String> = when (index) {
        0 -> listOf("[[:^S:]&[:^Z:]]", "[:digit:]", " ")
        1 -> listOf("[A-Z😀]", "[0-9𝟘]", "::")
        2 -> listOf("[]", "[:digit:]", "gap")
        else -> listOf("[:any:]", "[:any:]", "")
    }
    private fun symbols(index: Int): Pair<JSymbols, DecimalFormatSymbols> {
        val j = JSymbols(JLocale.ENGLISH); val k = DecimalFormatSymbols(ULocale("en"))
        for (before in listOf(false, true)) for ((item, value) in settings(index).withIndex()) {
            j.setPatternForCurrencySpacing(item, before, value)
            k.setPatternForCurrencySpacing(item, before, value)
        }
        return j to k
    }
    @Test fun preparedAndInPlaceSpacingMatchSource() {
        for (setting in 0..3) {
            val (js, ks) = symbols(setting)
            for (prefix in listOf("", "USD", "$", "😀", " USD")) for (suffix in listOf("", "EUR", "€", "😀", "EUR ")) {
                for (mode in 0..3) for (overwrite in listOf(false, true)) {
                    val jp = jBuffer(prefix, mode); val kp = kBuffer(prefix, mode)
                    val jx = jBuffer(suffix, mode); val kx = kBuffer(suffix, mode)
                    val j = JSpacing(jp, jx, overwrite, true, js)
                    val k = CurrencySpacingEnabledModifier(kp, kx, overwrite, true, ks)
                    assertEquals(j.prefixLength, k.getPrefixLength())
                    assertEquals(j.codePointCount, k.getCodePointCount())
                    assertEquals(j.isStrong, k.isStrong())
                    assertEquals(j.toString(), k.toString())
                    assertEquals(j.containsField(JField.CURRENCY), k.containsField(NumberFormatField.CURRENCY))
                    jp.clear(); kp.clear(); jx.clear(); kx.clear()
                    for (number in listOf("", "1", "12", "A1", "١٢", "𝟘", "\ud800")) for (offset in 0..1) {
                        val label = "$setting/$prefix/$suffix/$mode/$overwrite/$number/$offset"
                        val text = if (offset == 0) number else "[$number]"
                        val jb = jBuffer(text, 3); val kb = kBuffer(text, 3)
                        assertEquals(outcome { j.apply(jb, offset, offset + number.length) }, outcome { k.apply(kb, offset, offset + number.length) }, label)
                        compare(jb, kb, label)
                        val ji = jBuffer("[", 0); val ki = kBuffer("[", 0)
                        ji.append(jBuffer(prefix, mode)); ki.append(kBuffer(prefix, mode))
                        ji.append(number, JField.INTEGER); ki.append(number, NumberFormatField.INTEGER)
                        ji.append(jBuffer(suffix, mode)); ki.append(kBuffer(suffix, mode))
                        ji.append("]", null); ki.append("]", null)
                        assertEquals(outcome { JSpacing.applyCurrencySpacing(ji, 1, prefix.length, 1 + prefix.length + number.length, suffix.length, js) },
                            outcome { CurrencySpacingEnabledModifier.applyCurrencySpacing(ki, 1, prefix.length, 1 + prefix.length + number.length, suffix.length, ks) }, label)
                        compare(ji, ki, label)
                    }
                }
            }
        }
    }
    @Test fun symbolCallbacksLazySetsAndFailureState() {
        for (failure in listOf(null, "false:0", "false:1", "false:2", "true:0", "true:1", "true:2")) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val js = object : JSymbols(JLocale.ENGLISH) {
                override fun getPatternForCurrencySpacing(itemType: Int, beforeCurrency: Boolean): String? {
                    val key = "$beforeCurrency:$itemType"; jt.add(key)
                    if (key == failure) throw IllegalStateException("callback")
                    return super.getPatternForCurrencySpacing(itemType, beforeCurrency)
                }
            }
            val ks = object : DecimalFormatSymbols(ULocale("en")) {
                override fun getPatternForCurrencySpacing(itemType: Int, beforeCurrency: Boolean): String? {
                    val key = "$beforeCurrency:$itemType"; kt.add(key)
                    if (key == failure) throw IllegalStateException("callback")
                    return super.getPatternForCurrencySpacing(itemType, beforeCurrency)
                }
            }
            for (mode in listOf(0, 1)) for (number in listOf("", "1", "12")) {
                jt.clear(); kt.clear()
                val j = outcome { JSpacing(jBuffer("USD", mode), jBuffer("EUR", mode), false, false, js); "ok" }
                val k = outcome { CurrencySpacingEnabledModifier(kBuffer("USD", mode), kBuffer("EUR", mode), false, false, ks); "ok" }
                assertEquals(j, k); assertEquals(jt, kt)
                jt.clear(); kt.clear()
                val jb = jBuffer("USD${number}EUR", mode); val kb = kBuffer("USD${number}EUR", mode)
                assertEquals(outcome { JSpacing.applyCurrencySpacing(jb, 0, 3, 3 + number.length, 3, js) },
                    outcome { CurrencySpacingEnabledModifier.applyCurrencySpacing(kb, 0, 3, 3 + number.length, 3, ks) })
                assertEquals(jt, kt); compare(jb, kb)
            }
        }
    }
    @Test fun snapshotsNullsMalformedPatternsAndInheritedEquality() {
        val (js, ks) = symbols(0)
        val j = JSpacing(jBuffer("USD", 1), jBuffer("EUR", 1), false, false, js)
        val k = CurrencySpacingEnabledModifier(kBuffer("USD", 1), kBuffer("EUR", 1), false, false, ks)
        for (before in listOf(false, true)) {
            js.setPatternForCurrencySpacing(2, before, "new"); ks.setPatternForCurrencySpacing(2, before, "new")
        }
        val j2 = JSpacing(jBuffer("USD", 1), jBuffer("EUR", 1), false, false, js)
        val k2 = CurrencySpacingEnabledModifier(kBuffer("USD", 1), kBuffer("EUR", 1), false, false, ks)
        assertEquals(j.strictEquals(j2), k.strictEquals(k2)) // Inherited equality ignores spacing settings.
        for (number in listOf("1", "12")) {
            val jb = jBuffer(number, 3); val kb = kBuffer(number, 3)
            assertEquals(j.apply(jb, 0, number.length), k.apply(kb, 0, number.length)); compare(jb, kb)
        }
        for (pattern in listOf(null, "[", "not a set", "[]")) for (item in 0..2) {
            js.setPatternForCurrencySpacing(item, false, pattern); ks.setPatternForCurrencySpacing(item, false, pattern)
            assertEquals(outcome { JSpacing(jBuffer("USD", 1), jBuffer("", 0), false, false, js); "ok" },
                outcome { CurrencySpacingEnabledModifier(kBuffer("USD", 1), kBuffer("", 0), false, false, ks); "ok" })
            val jb = jBuffer("USD1", 1); val kb = kBuffer("USD1", 1)
            assertEquals(outcome { JSpacing.applyCurrencySpacing(jb, 0, 3, 4, 0, js) },
                outcome { CurrencySpacingEnabledModifier.applyCurrencySpacing(kb, 0, 3, 4, 0, ks) }); compare(jb, kb)
            val value = settings(0)[item]
            js.setPatternForCurrencySpacing(item, false, value); ks.setPatternForCurrencySpacing(item, false, value)
        }
        for (args in listOf(listOf(0, 0, 0, 0), listOf(0, 3, 3, 3), listOf(0, 1, 2, 0))) {
            assertEquals(outcome { JSpacing.applyCurrencySpacing(null, args[0], args[1], args[2], args[3], null) },
                outcome { CurrencySpacingEnabledModifier.applyCurrencySpacing(null, args[0], args[1], args[2], args[3], null) })
        }
        assertEquals(outcome { j.apply(null, 0, 0) }, outcome { k.apply(null, 0, 0) })
        assertEquals(outcome { JSpacing(null, jBuffer("", 0), false, false, null) },
            outcome { CurrencySpacingEnabledModifier(null, kBuffer("", 0), false, false, null) })
    }
}
