package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class UConverterAliasTest {
    private fun aliases(part: Int) {
        for ((index, row) in converterAliasGoldenRows.withIndex()) {
            if (index % 4 != part) continue
            val fields = row.split('|')
            val alias = fields[0]
            val count = UConverterAlias.countAliases(alias)
            assertEquals(fields[1].toInt(), count, alias)
            assertEquals(fields[2], (0..<count).joinToString(";") { UConverterAlias.getAlias(alias, it) ?: "null" }, alias)
            assertEquals(fields[3], converterAliasStandards.flatMap { standard -> listOf(UConverterAlias.getStandardName(alias, standard), UConverterAlias.getCanonicalName(alias, standard)) }.joinToString(";") { it ?: "null" }, alias)
        }
    }
    @Test fun allAliasAndStandardGoldensPartOne() = aliases(0)
    @Test fun allAliasAndStandardGoldensPartTwo() = aliases(1)
    @Test fun allAliasAndStandardGoldensPartThree() = aliases(2)
    @Test fun allAliasAndStandardGoldensPartFour() = aliases(3)
    @Test fun availableNamesInputValidationAndNormalization() {
        assertEquals(converterAvailableNames.size, UConverterAlias.countAvailable())
        for ((index, name) in converterAvailableNames.withIndex()) assertEquals(name, UConverterAlias.getAvailableName(index))
        for (index in listOf(Int.MIN_VALUE, -1, converterAvailableNames.size, 65535, 65536, Int.MAX_VALUE)) assertNull(UConverterAlias.getAvailableName(index))
        assertEquals("Alias param is null!", assertFailsWith<IllegalArgumentException> { UConverterAlias.countAliases(null) }.message)
        assertFailsWith<IllegalArgumentException> { UConverterAlias.getAlias(null, 0) }
        assertFailsWith<IllegalArgumentException> { UConverterAlias.getCanonicalName(null, "IANA") }
        assertFailsWith<IllegalArgumentException> { UConverterAlias.getStandardName(null, "IANA") }
        assertNull(UConverterAlias.getStandardName("", null))
        assertNull(UConverterAlias.getCanonicalName("", null))
        assertFails { UConverterAlias.getStandardName("UTF-8", null) }
        assertFails { UConverterAlias.getCanonicalName("UTF-8", null) }
        for (index in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) assertFails { UConverterAlias.getAlias("UTF-8", index) }
        assertEquals("prefixutf8", UConverterAlias.stripForCompare(StringBuilder("prefix"), "U.t_F 0008").toString())
        assertEquals("ibm37", UConverterAlias.stripForCompare(StringBuilder(), "iBM-00037").toString())
        // These results preserve two observable comparator quirks in ICU4J.
        assertEquals(0, UConverterAlias.compareNames("a", "b"))
        assertEquals(0, UConverterAlias.compareNames("UTF-8", "UTF-7"))
        assertFails { UConverterAlias.compareNames("abc", "0") }
    }
    @Test fun aliasReaderTocAndAuthentication() {
        val bytes = ICUBinary.getRequiredData("cnvalias.icu")
        val reader = UConverterAliasDataReader(bytes)
        assertTrue(reader.isDataVersionAcceptable(byteArrayOf(3, 0, 1)))
        assertTrue(reader.isDataVersionAcceptable(byteArrayOf(3, 0, 1, 99)))
        for (version in listOf(byteArrayOf(), byteArrayOf(3, 0), byteArrayOf(2, 0, 1), byteArrayOf(3, 1, 1), byteArrayOf(3, 0, 2))) assertFalse(reader.isDataVersionAcceptable(version))
        val toc = reader.readToc(10)
        assertEquals(9, toc[0])
        assertEquals(converterAvailableNames.size, toc[1])
        val corrupt = ICUBinary.getRequiredData("cnvalias.icu").bytes
        corrupt[18] = 2
        assertFails { UConverterAliasDataReader(BinaryBuffer(corrupt)) }
    }
}
