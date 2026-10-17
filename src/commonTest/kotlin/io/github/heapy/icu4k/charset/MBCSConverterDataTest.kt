package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceLoader
import io.github.heapy.icu4k.impl.InvalidFormatException
import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class MBCSConverterDataTest {
    private fun goldens(part: Int) {
        for ((index, row) in mbcsLoaderGoldenRows.withIndex()) if (index % 4 == part) {
            val name = row.substringBefore('|')
            assertEquals(row, mbcsLoaderFingerprint(name, MBCSConverterData.load(name)), name)
        }
    }
    @Test fun allConverterTablesAndOptionsPartOne() = goldens(0)
    @Test fun allConverterTablesAndOptionsPartTwo() = goldens(1)
    @Test fun allConverterTablesAndOptionsPartThree() = goldens(2)
    @Test fun allConverterTablesAndOptionsPartFour() = goldens(3)
    @Test fun baseTablesUseTheSameLoaderAndOwnMutableTables() {
        val visited = mutableListOf<String>()
        val loader = ICUResourceLoader { path ->
            visited += path
            assertTrue(path.startsWith("custom/"))
            ICUData.getBytes(path.substringAfterLast('/'))?.let { BinaryBuffer(it) }
        }
        val name = "ibm-943_P130-1999"
        val first = MBCSConverterData.load(name, "custom", loader)
        assertEquals(listOf("custom/$name.cnv", "custom/ibm-943_P15A-2003.cnv"), visited)
        val second = MBCSConverterData.load(name)
        assertNotSame(first.sharedData.mbcs, second.sharedData.mbcs)
        val table = first.sharedData.mbcs
        assertSame(table, assertNotNull(table.baseSharedData).mbcs)
        val original = second.sharedData.mbcs.stateTable[0][0]
        table.stateTable[0][0] = original.inv()
        assertEquals(original, second.sharedData.mbcs.stateTable[0][0])
        assertFailsWith<InvalidFormatException> { MBCSConverterData.load("not-a-converter") }
        assertFailsWith<InvalidFormatException> { MBCSConverterData.load("missing", "custom", ICUResourceLoader { null }) }
    }
    @Test fun rejectsInvalidMetadataHeadersAndExtensionRecursion() {
        fun altered(modify: (BinaryBuffer, Int) -> Unit): ICUResourceLoader {
            val raw = ICUData.getBytes("ibm-37_P100-1995.cnv")!!
            val bytes = BinaryBuffer(raw)
            val reader = UConverterDataReader(bytes)
            val start = bytes.position
            reader.readStaticData(UConverterStaticData())
            modify(bytes, start)
            return ICUResourceLoader { BinaryBuffer(raw.copyOf()) }
        }
        for (loader in listOf(
            altered { bytes, start -> bytes.position = start; bytes.writeInt(99) },
            altered { bytes, start -> bytes.position = start + 69; bytes.writeByte(1) },
            altered { bytes, _ -> bytes.writeByte(3) },
            altered { bytes, _ -> bytes.position += 24; bytes.writeInt(7) },
        )) assertFailsWith<InvalidFormatException> { MBCSConverterData.load("custom", "custom", loader) }
        val extension = ICUData.getBytes("ibm-943_P130-1999.cnv")!!
        val calls = mutableListOf<String>()
        assertFailsWith<InvalidFormatException> {
            MBCSConverterData.load("extension", "custom", ICUResourceLoader { calls += it; BinaryBuffer(extension.copyOf()) })
        }
        assertEquals(2, calls.size)
    }
}

internal fun mbcsLoaderFingerprint(request: String, data: MBCSConverterData): String {
    val table = data.sharedData.mbcs
    val values = mutableListOf(request, data.name, data.options.toString(), data.maxBytesPerChar.toString(), data.minBytesPerChar.toString(), data.substitution.contentHashCode().toString(), data.subChar1.toString())
    values += listOf(table.countStates.toString(), table.dbcsOnlyState.toString(), table.stateTableOwned.toString(), table.countToUFallbacks.toString(), table.fromUBytesLength.toString(), table.outputType.toString(), table.unicodeMask.toString(), table.utf8Friendly.toString(), table.maxFastUChar.code.toString(), table.asciiRoundtrips.toString(), table.stateTable.size.toString(), (table.baseSharedData?.staticData?.name?.hashCode() ?: 0).toString(), (table.baseSharedData?.mbcs === table).toString())
    values += listOf(table.stateTable.contentDeepHashCode(), table.unicodeCodeUnits.contentHashCode(), table.fromUnicodeTable.contentHashCode(), table.fromUnicodeTableInts?.contentHashCode() ?: 0, table.fromUnicodeBytes?.contentHashCode() ?: 0, table.fromUnicodeChars?.contentHashCode() ?: 0, table.fromUnicodeInts?.contentHashCode() ?: 0, table.swapLFNLStateTable?.contentDeepHashCode() ?: 0, table.swapLFNLFromUnicodeChars?.contentHashCode() ?: 0).map { it.toString() }
    values += table.swapLFNLName ?: "null"
    var fallbacks = 1
    for (fallback in table.toUFallbacks) { fallbacks = 31 * fallbacks + fallback.offset; fallbacks = 31 * fallbacks + fallback.codePoint }
    values += fallbacks.toString()
    values += listOf((table.extIndexes?.position ?: 0).toString(), (table.extIndexes?.remaining ?: 0).toString(), (table.extIndexes?.let { it.slice().readBytes(it.remaining).contentHashCode() } ?: 0).toString(), (table.mbcsIndex == null).toString())
    return values.joinToString("|")
}
