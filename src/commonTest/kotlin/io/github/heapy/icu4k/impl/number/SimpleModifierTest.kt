// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.SimpleFormatterImpl
import io.github.heapy.icu4k.impl.number.range.PrefixInfixSuffixLengthHelper
import io.github.heapy.icu4k.text.NumberFormatField
import kotlin.test.*

class SimpleModifierTest {
    private fun compile(pattern: String) = SimpleFormatterImpl.compileToStringMinMaxArguments(pattern, StringBuilder(), 0, 2)
    @Test fun prefixesSuffixesAndLiteralOverwrite() {
        val output = FormattedStringBuilder().apply { append("123", NumberFormatField.INTEGER) }
        val mod = SimpleModifier(compile("😀{0}%"), NumberFormatField.PERCENT, false)
        assertEquals(3, mod.apply(output, 0, 3))
        assertEquals("😀123%", output.toString())
        assertEquals(2, mod.getPrefixLength())
        assertEquals(2, mod.getCodePointCount())
        assertEquals(-1, SimpleModifier(compile("x"), null, false).apply(output, 2, 4))
        assertEquals("😀x3%", output.toString())
        assertEquals(0, SimpleModifier(compile("{0}"), null, false).apply(null, 0, 0))
    }
    @Test fun twoArgumentLiteralsAndRelativeIndexes() {
        val output = FormattedStringBuilder().apply { append("[]", null) }
        val helper = PrefixInfixSuffixLengthHelper().apply { length1 = 2; length2 = 3 }
        SimpleModifier.formatTwoArgPattern(compile("pre{0}–{1}suf"), output, 1, helper, NumberFormatField.MEASURE_UNIT)
        assertEquals("[pre–suf]", output.toString())
        assertEquals(listOf(3, 5, 6, 9, 12), listOf(helper.index0(), helper.index1(), helper.index2(), helper.index3(), helper.index4()))
        SimpleModifier.formatTwoArgPattern(compile("{0}{1}"), null, 0, helper, null)
        assertEquals(0, helper.lengthPrefix + helper.lengthInfix + helper.lengthSuffix)
    }
}
