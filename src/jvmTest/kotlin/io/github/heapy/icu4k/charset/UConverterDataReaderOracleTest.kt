package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.impl.ICUBinary
import java.nio.ByteBuffer
import java.nio.CharBuffer
import kotlin.test.*

class UConverterDataReaderOracleTest {
    private val readerType = Class.forName("com.ibm.icu.charset.UConverterDataReader")
    private val staticType = Class.forName("com.ibm.icu.charset.UConverterStaticData")
    private val headerType = Class.forName("com.ibm.icu.charset.CharsetMBCS\$MBCSHeader")
    private val tableType = Class.forName("com.ibm.icu.charset.CharsetMBCS\$UConverterMBCSTable")
    private fun make(type: Class<*>) = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    private fun get(value: Any, name: String): Any? = value.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(value)
    private fun int(value: Any, name: String) = (get(value, name) as Number).toInt()
    private fun invoke(value: Any, name: String, vararg args: Any): Any? = readerType.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(value, *args)
    @Test fun allSerializedArraysAndResourceBytesEqualSource() {
        for (row in converterDataGoldenRows) {
            val name = row.substringBefore('|')
            val original = javaClass.classLoader.getResourceAsStream("com/ibm/icu/impl/data/icudata/$name")!!.use { it.readBytes() }
            val bytes = ICUBinary.getRequiredData(name)
            assertContentEquals(original, bytes.slice().readBytes(bytes.remaining), name)
            val sourceBytes = ByteBuffer.wrap(original)
            val source = readerType.getDeclaredConstructor(ByteBuffer::class.java).apply { isAccessible = true }.newInstance(sourceBytes)
            val port = UConverterDataReader(bytes)
            val sourceStatic = make(staticType); invoke(source, "readStaticData", sourceStatic)
            val data = UConverterStaticData(); port.readStaticData(data)
            assertEquals(get(sourceStatic, "name"), data.name, name)
            val staticFields = listOf("structSize", "codepage", "platform", "conversionType", "minBytesPerChar", "maxBytesPerChar", "subCharLen", "hasToUnicodeFallback", "hasFromUnicodeFallback", "unicodeMask", "subChar1")
            assertEquals(staticFields.map { int(sourceStatic, it) }, listOf(data.structSize, data.codepage, data.platform.toInt(), data.conversionType.toInt(), data.minBytesPerChar.toInt(), data.maxBytesPerChar.toInt(), data.subCharLen.toInt(), data.hasToUnicodeFallback.toInt(), data.hasFromUnicodeFallback.toInt(), data.unicodeMask.toInt(), data.subChar1.toInt()), name)
            assertContentEquals(get(sourceStatic, "subChar") as ByteArray, data.subChar, name)
            assertContentEquals(get(sourceStatic, "reserved") as ByteArray, data.reserved, name)
            val sourceHeader = make(headerType); invoke(source, "readMBCSHeader", sourceHeader)
            val header = MBCSHeader(); port.readMBCSHeader(header)
            assertContentEquals(get(sourceHeader, "version") as ByteArray, header.version, name)
            val headerFields = listOf("countStates", "countToUFallbacks", "offsetToUCodeUnits", "offsetFromUTable", "offsetFromUBytes", "flags", "fromUBytesLength", "options", "fullStage2Length")
            assertEquals(headerFields.map { int(sourceHeader, it) }, listOf(header.countStates, header.countToUFallbacks, header.offsetToUCodeUnits, header.offsetFromUTable, header.offsetFromUBytes, header.flags, header.fromUBytesLength, header.options, header.fullStage2Length), name)
            assertEquals(invoke(source, "dataFormatHasUnicodeMask"), port.dataFormatHasUnicodeMask(), name)
            val output = header.flags.toByte().toShort()
            if (output == 14.toShort()) assertEquals(invoke(source, "readBaseTableName"), port.readBaseTableName(), name) else {
                val sourceTable = make(tableType)
                val table = UConverterMBCSTable()
                table.outputType = output
                table.unicodeMask = if (port.dataFormatHasUnicodeMask()) (data.unicodeMask.toInt() and 3).toShort() else 3
                for ((field, value) in listOf("outputType" to output, "unicodeMask" to table.unicodeMask)) tableType.getDeclaredField(field).apply { isAccessible = true }.setShort(sourceTable, value)
                invoke(source, "readMBCSTable", sourceHeader, sourceTable); port.readMBCSTable(header, table)
                assertEquals(int(sourceTable, "countStates"), table.countStates.toInt(), name)
                assertEquals(int(sourceTable, "countToUFallbacks"), table.countToUFallbacks, name)
                assertEquals(int(sourceTable, "fromUBytesLength"), table.fromUBytesLength, name)
                @Suppress("UNCHECKED_CAST") val states = get(sourceTable, "stateTable") as Array<IntArray>
                assertEquals(states.size, table.stateTable.size, name)
                for (i in states.indices) assertContentEquals(states[i], table.stateTable[i], name)
                assertContentEquals(get(sourceTable, "unicodeCodeUnits") as CharArray?, table.unicodeCodeUnits, name)
                assertContentEquals(get(sourceTable, "fromUnicodeTable") as CharArray?, table.fromUnicodeTable, name)
                assertContentEquals(get(sourceTable, "fromUnicodeTableInts") as IntArray?, table.fromUnicodeTableInts, name)
                assertContentEquals(get(sourceTable, "fromUnicodeBytes") as ByteArray?, table.fromUnicodeBytes, name)
                assertContentEquals(get(sourceTable, "fromUnicodeChars") as CharArray?, table.fromUnicodeChars, name)
                assertContentEquals(get(sourceTable, "fromUnicodeInts") as IntArray?, table.fromUnicodeInts, name)
                val index = get(sourceTable, "mbcsIndex") as CharBuffer?
                assertContentEquals(index?.let { CharArray(it.remaining()) { n -> it.get(it.position() + n) } }, table.mbcsIndex?.let { it.slice().readChars(it.remaining / 2) }, name)
                val fallbacks = get(sourceTable, "toUFallbacks") as Array<*>
                assertEquals(fallbacks.map { int(it!!, "offset") to int(it, "codePoint") }, table.toUFallbacks.map { it.offset to it.codePoint }, name)
            }
            assertEquals(invoke(source, "bytesReadAfterStaticData"), port.bytesReadAfterStaticData(), name)
            val offset = header.flags ushr 8
            if (offset != 0) {
                val extension = invoke(source, "readExtIndexes", offset - port.bytesReadAfterStaticData()) as ByteBuffer
                val actual = port.readExtIndexes(offset - port.bytesReadAfterStaticData())
                val expected = ByteArray(extension.remaining()).also { extension.get(it) }
                assertContentEquals(expected, actual.slice().readBytes(actual.remaining), name)
            }
            assertEquals(sourceBytes.position(), bytes.position, name)
            assertEquals(invoke(source, "bytesReadAfterStaticData"), port.bytesReadAfterStaticData(), name)
        }
    }
}
