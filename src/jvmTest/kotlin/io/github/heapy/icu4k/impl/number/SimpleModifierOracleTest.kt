@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.SimpleFormatterImpl as JFormatter
import com.ibm.icu.impl.FormattedStringBuilder as JBuilder
import com.ibm.icu.impl.number.SimpleModifier as JModifier
import com.ibm.icu.impl.number.range.PrefixInfixSuffixLengthHelper as JHelper
import com.ibm.icu.text.NumberFormat.Field as JField
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.range.PrefixInfixSuffixLengthHelper
import io.github.heapy.icu4k.text.NumberFormatField
import kotlin.test.*

class SimpleModifierOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { if (e is IndexOutOfBoundsException) "bounds" else e.javaClass.simpleName }
    private fun compile(pattern: String) = JFormatter.compileToStringMinMaxArguments(pattern, StringBuilder(), 0, 2)
    @Test fun oneArgumentAndLiteralPatternsMatchSource() {
        for (text in listOf("", "literal", "{0}", "pre{0}", "{0}suf", "😀{0}日本", "'{'{0}'}'", "\ud800{0}\udc00")) {
            val pattern = compile(text)
            val j = JModifier(pattern, JField.CURRENCY, true); val k = SimpleModifier(pattern, NumberFormatField.CURRENCY, true)
            assertEquals(j.prefixLength, k.getPrefixLength(), text)
            assertEquals(j.codePointCount, k.getCodePointCount(), text)
            assertEquals(j.isStrong, k.isStrong())
            assertNull(k.getParameters())
            assertEquals(j.strictEquals(JModifier(pattern, JField.CURRENCY, true)), k.strictEquals(SimpleModifier(pattern, NumberFormatField.CURRENCY, true)))
            assertEquals(outcome { j.containsField(null) }, outcome { k.containsField(null) })
            assertEquals(outcome { j.apply(null, 0, 0) }, outcome { k.apply(null, 0, 0) })
            for (left in 0..4) for (right in left..4) {
                val jb = JBuilder().apply { append("0123", JField.INTEGER) }; val kb = FormattedStringBuilder().apply { append("0123", NumberFormatField.INTEGER) }
                assertEquals(outcome { j.apply(jb, left, right) }, outcome { k.apply(kb, left, right) }, "$text/$left/$right")
                assertEquals(jb.toDebugString(), kb.toDebugString())
            }
        }
    }
    @Test fun twoArgumentPatternsLengthHelpersAndPartialFailuresMatchSource() {
        for (text in listOf("{0}{1}", "{0}–{1}", "pre{0}{1}", "{0}{1}suf", "😀{0}日本{1}%", "pre{1} – {0}suf", "{0}", "literal")) for (nullBuffer in listOf(false, true)) for (nullHelper in listOf(false, true)) {
            val pattern = compile(text)
            val j = JBuilder().apply { append("[]", null) }; val k = FormattedStringBuilder().apply { append("[]", null) }
            val jh = JHelper().apply { lengthPrefix = 91; lengthInfix = 92; lengthSuffix = 93; length1 = 2; length2 = 3 }
            val kh = PrefixInfixSuffixLengthHelper().apply { lengthPrefix = 91; lengthInfix = 92; lengthSuffix = 93; length1 = 2; length2 = 3 }
            assertEquals(outcome { JModifier.formatTwoArgPattern(pattern, if (nullBuffer) null else j, 1, if (nullHelper) null else jh, JField.CURRENCY) },
                outcome { SimpleModifier.formatTwoArgPattern(pattern, if (nullBuffer) null else k, 1, if (nullHelper) null else kh, NumberFormatField.CURRENCY) }, "$text/$nullBuffer/$nullHelper")
            assertEquals(j.toDebugString(), k.toDebugString())
            assertEquals(listOf(jh.index0(), jh.index1(), jh.index2(), jh.index3(), jh.index4()), listOf(kh.index0(), kh.index1(), kh.index2(), kh.index3(), kh.index4()))
        }
    }
}
