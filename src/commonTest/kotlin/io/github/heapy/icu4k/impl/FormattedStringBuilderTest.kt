@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import kotlin.test.*

class FormattedStringBuilderTest {
    @Test fun centeredEditsFieldsCodePointsAndCopies() {
        val value = FormattedStringBuilder(2)
        value.append("12", NumberFormatField.INTEGER)
        value.insertChar16(0, '-', NumberFormatField.SIGN)
        value.appendCodePoint(0x1f600, null)
        assertEquals("-12😀", value.toString())
        assertEquals(4, value.codePointCount())
        assertEquals(0x1f600, value.getLastCodePoint())
        assertEquals('-'.code, value.getFirstCodePoint())
        assertSame(NumberFormatField.INTEGER, value.fieldAt(1))
        assertEquals("<FormattedStringBuilder [-12😀] [-iinn]>", value.toDebugString())
        val copy = FormattedStringBuilder(value)
        assertTrue(copy.contentEquals(value))
        value.splice(1, 3, "abc", 0, 3, NumberFormatField.CURRENCY)
        assertEquals("-abc😀", value.toString())
        assertEquals("-12😀", copy.toString())
        assertFailsWith<IllegalArgumentException> { value.append(value) }
        assertFailsWith<UnsupportedOperationException> { value.hashCode() }
        assertFailsWith<UnsupportedOperationException> { value.equals(value) }
    }
    @Test fun wrappedFieldsAppendOffsetsAndPrefixSuffix() {
        val value = FormattedStringBuilder()
        value.append("arg", NumberFormatField.INTEGER)
        val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments("pre{0}end", StringBuilder(), 1, 1)
        assertEquals(6, SimpleFormatterImpl.formatPrefixSuffix(compiled, NumberFormatField.CURRENCY, 0, 3, value))
        assertEquals("preargend", value.toString())
        assertEquals("<FormattedStringBuilder [preargend] [\$\$\$iii\$\$\$]>", value.toDebugString())
        val wrapped = FormattedStringBuilder.FieldWrapper { NumberFormatField.INTEGER }
        val other = FormattedStringBuilder().apply { append("arg", wrapped) }
        assertTrue(other.contentEquals(FormattedStringBuilder().apply { append("arg", NumberFormatField.INTEGER) }))
        other.setAppendIndex(1)
        other.setAppendableField(null)
        (other as Appendable).append('!')
        assertEquals("a!rg", other.toString())
        assertFailsWith<AssertionError> { FormattedStringBuilder.unwrapField("bad") }
    }
}
