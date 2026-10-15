// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import java.io.*
import kotlin.test.*
import com.ibm.icu.text.PluralFormat as Source
import com.ibm.icu.text.PluralRules as SourceRules
import com.ibm.icu.util.ULocale as SourceLocale

class PluralFormatOracleTest {
    @Test fun localeTypePatternAndNumericMatrix() {
        val patterns = listOf("one{one #} few{few #} many{many #} other{other #}", "offset:0.5 =1.5{explicit fraction}one{'#' #}other{# {n,number,'x' 0}}")
        for (locale in listOf("en_US", "fr_FR", "ru", "ar", "pl", "zh")) for (ordinal in listOf(false, true)) for (pattern in patterns) {
            val expected = Source(SourceLocale(locale), if (ordinal) SourceRules.PluralType.ORDINAL else SourceRules.PluralType.CARDINAL, pattern)
            val actual = PluralFormat(ULocale(locale), if (ordinal) PluralRules.PluralType.ORDINAL else PluralRules.PluralType.CARDINAL, pattern)
            for (number in listOf(-22.5, 0.0, 1.0, 2.0, 3.0, 7.0, 21.0, 1234.56, Double.NaN, Double.POSITIVE_INFINITY))
                assertEquals(expected.format(number), actual.format(number), "$locale/$ordinal/$pattern/$number")
            for (number in listOf<Number>(Long.MAX_VALUE, java.math.BigInteger("12345678901234567890123"), java.math.BigDecimal("1.00"))) {
                val buffer = StringBuilder("prefix ")
                actual.format(number, buffer, null)
                assertEquals(expected.format(number, StringBuffer("prefix "), null).toString(), buffer.toString())
            }
            val javaFormat: java.text.Format = actual
            assertEquals(expected.format(2L), javaFormat.format(2L))
        }
    }
    @Test fun cloneAliasAndFailurePolicy() {
        val pattern = "one{one} few{few} other{other}"
        val source = Source(SourceLocale.ENGLISH, pattern)
        val target = PluralFormat(ULocale.ENGLISH, pattern)
        val sourceCopy = source.clone() as Source
        val targetCopy = target.clone()
        source.setLocale(SourceLocale("ru")); target.setLocale(ULocale("ru"))
        assertEquals(sourceCopy.format(2.0), targetCopy.format(2.0))
        source.applyPattern(pattern); target.applyPattern(pattern)
        assertEquals(sourceCopy.format(2.0), targetCopy.format(2.0))
        assertEquals(source == sourceCopy, target == targetCopy)
        assertFailsWith<NullPointerException> { source.hashCode() }
        assertFailsWith<NullPointerException> { target.hashCode() }
        assertFailsWith<IllegalArgumentException> { source.format("x", null, null) }
        assertFailsWith<IllegalArgumentException> { target.format("x", null as StringBuilder?, null) }
        assertFailsWith<NullPointerException> { source.format(1, null, null) }
        assertFailsWith<NullPointerException> { target.format(1, null as StringBuilder?, null) }
        assertEquals(Source(java.util.Locale.US).format(1234.5), PluralFormat(java.util.Locale.US).format(1234.5))
    }
    @Test fun streamsRebuildPatternAndSelector() {
        val descriptor = ObjectStreamClass.lookup(PluralFormat::class.java)
        val sourceDescriptor = ObjectStreamClass.lookup(Source::class.java)
        assertEquals(sourceDescriptor.serialVersionUID, descriptor.serialVersionUID)
        assertEquals(sourceDescriptor.fields.map { it.name }, descriptor.fields.map { it.name })
        assertEquals(sourceDescriptor.fields.map { it.type.simpleName }, descriptor.fields.map { it.type.simpleName })
        val pattern = "one{#st} two{#nd} few{#rd} other{#th}"
        val source = Source(SourceLocale.ENGLISH, SourceRules.PluralType.ORDINAL, pattern)
        val target = PluralFormat(ULocale.ENGLISH, PluralRules.PluralType.ORDINAL, pattern)
        val sourceCopy = roundtrip(source) as Source
        val targetCopy = roundtrip(target) as PluralFormat
        for (n in listOf(1.0, 2.0, 3.0, 21.0, 1234.5)) assertEquals(sourceCopy.format(n), targetCopy.format(n))
        assertEquals(source, sourceCopy)
        assertEquals(target, targetCopy)
        assertFailsWith<NullPointerException> { targetCopy.hashCode() }
        assertEquals((roundtrip(Source(SourceLocale.US)) as Source).format(12.5), (roundtrip(PluralFormat(ULocale.US)) as PluralFormat).format(12.5))
    }
    private fun roundtrip(value: Any): Any {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    }
}
