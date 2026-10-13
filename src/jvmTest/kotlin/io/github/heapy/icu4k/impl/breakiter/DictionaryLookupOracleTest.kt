package com.ibm.icu.impl.breakiter

import io.github.heapy.icu4k.impl.ICUBinary as KBinary
import io.github.heapy.icu4k.impl.ICUResourceBundleReader as KReader
import io.github.heapy.icu4k.impl.breakiter.DictionaryData as KData
import io.github.heapy.icu4k.text.StringCharacterIterator as KCursor
import kotlin.test.*

class DictionaryLookupOracleTest {
    @Test fun rootDictionaryMappingsAndLoadedMatchersMatchOriginal() {
        val root = KReader(KBinary.getRequiredData("brkitr/root.res"))
        val dictionaries = root.getTable(root.getTable(root.rootResource)!!.getResource("dictionaries"))!!
        assertTrue(dictionaries.size >= 5)
        val samples = listOf("ภาษาไทย", "ພາສາລາວ", "ភាសាខ្មែរ", "မြန်မာဘာသာ", "日本語中文", "カタカナ", "한국어", "A😀B")
        for (i in 0 until dictionaries.size) {
            val script = dictionaries.getKey(i)!!
            val j = DictionaryData.loadDictionaryFor(script)
            val k = assertNotNull(KData.loadDictionaryFor(script))
            assertEquals(j.type, k.getType())
            for (source in samples) for (max in listOf(0, 1, 2, source.length, source.length + 5)) {
                val jt = java.text.StringCharacterIterator(source); val kt = KCursor(source)
                val jl = IntArray(30); val kl = IntArray(30)
                val jv = IntArray(30); val kv = IntArray(30)
                val jc = intArrayOf(-1); val kc = intArrayOf(-1)
                assertEquals(j.matches(jt, max, jl, jc, 20, jv), k.matches(kt, max, kl, kc, 20, kv), script)
                assertContentEquals(jl, kl); assertContentEquals(jv, kv); assertContentEquals(jc, kc)
                assertEquals(jt.index, kt.getIndex())
            }
        }
        for (script in listOf("", "Unknown", "Latn", "thai", "Thai/missing")) {
            assertFailsWith<java.util.MissingResourceException> { DictionaryData.loadDictionaryFor(script) }
            assertFailsWith<io.github.heapy.icu4k.util.MissingResourceException> { KData.loadDictionaryFor(script) }
        }
    }
}
