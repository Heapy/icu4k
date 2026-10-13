package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class DictionaryDataTest {
    @Test fun loadsRealDictionariesOnEveryTarget() {
        for (name in listOf("thaidict", "laodict", "khmerdict", "burmesedict", "cjdict")) {
            val data = assertNotNull(DictionaryData.read(ICUBinary.getRequiredData("brkitr/$name.dict")))
            assertTrue(data.indexes[DictionaryData.IX_TOTAL_SIZE] > data.indexes[DictionaryData.IX_STRING_TRIE_OFFSET])
            when (data) {
                is DictionaryData.TrieData.Bytes -> {
                    assertTrue(data.data.isNotEmpty())
                    assertEquals(0, data.matcher().getType())
                }
                is DictionaryData.TrieData.Chars -> {
                    assertTrue(data.data.isNotEmpty())
                    assertEquals(1, data.matcher().getType())
                    val count = IntArray(1)
                    data.matcher().matches(StringCharacterIterator("日本語"), 3, IntArray(20), count, 20, null)
                    assertTrue(count[0] > 0)
                }
            }
        }
        val thai = DictionaryData.read(ICUBinary.getRequiredData("brkitr/thaidict.dict")) as DictionaryData.TrieData.Bytes
        val count = IntArray(1)
        val lengths = IntArray(20)
        val cursor = StringCharacterIterator("ภาษาไทย")
        assertTrue(thai.matcher().matches(cursor, 7, lengths, count, 20, null) > 0)
        assertTrue(count[0] > 0)
    }
    @Test fun resolvesScriptDictionariesThroughOriginalResourceTable() {
        for (script in listOf("Thai", "Laoo", "Khmr", "Mymr", "Hira")) {
            assertNotNull(DictionaryData.loadDictionaryFor(script))
        }
        assertFailsWith<io.github.heapy.icu4k.util.MissingResourceException> {
            DictionaryData.loadDictionaryFor("Latn")
        }
    }

}
