package io.github.heapy.icu4k.impl

import kotlin.random.Random
import kotlin.test.*

class ResourceCacheTest {
    private class Item(val number: Int)
    @Test fun sortedTableAndRadixTreeKeepAllResourceWidthsDistinct() {
        try {
            CacheValue.setStrength(CacheValue.Strength.STRONG)
            for (max in listOf(8, 31, 127, 255, 1023, 0xfffffff)) {
                val cache = ResourceCache(max)
                val offsets = if (max <= 1023) (1..max).toList() else (1..1023).map { it * 131071 }
                val entries = offsets.flatMap { offset -> listOf(0, 6, 5, 9).map { type -> ((type shl 28) or offset) to Item(offset) } }
                for ((res, item) in entries.shuffled(Random(42))) {
                    assertNull(cache.get(res))
                    assertSame(item, cache.putIfAbsent(res, item, 24))
                }
                for ((res, item) in entries) {
                    assertSame(item, cache.get(res))
                    assertSame(item, cache.putIfAbsent(res, Item(-1), 0))
                }
            }
        } finally { CacheValue.setStrength(CacheValue.Strength.SOFT) }
    }
    @Test fun readerCachesStringAliasAndContainerIdentities() {
        val reader = ICUResourceBundleReader.getReader(ICUData.ICU_BRKITR_BASE_NAME, "root")!!
        val root = reader.getTable(reader.rootResource)!!
        assertSame(root, reader.getTable(reader.rootResource))
        val value = ICUResourceBundleReader.ReaderValue().also { it.reader = reader; it.res = reader.rootResource }
        assertSame(value.getTable(), value.getTable())
        val dictionaries = reader.getTable(root.getResource("dictionaries"))!!
        val res = dictionaries.getResource("Mymr")
        assertSame(reader.getString(res), reader.getString(res))
        val japanese = ICUResourceBundleReader.getReader(ICUData.ICU_BRKITR_BASE_NAME, "ja")!!
        val table = japanese.getTable(japanese.rootResource)!!
        val arrayRes = table.getResource("extensions")
        assertSame(japanese.getArray(arrayRes), japanese.getArray(arrayRes))
        value.reader = japanese; value.res = arrayRes
        assertSame(value.getArray(), value.getArray())
    }
}
