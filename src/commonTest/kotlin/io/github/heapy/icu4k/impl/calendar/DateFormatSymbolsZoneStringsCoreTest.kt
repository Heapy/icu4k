package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DateFormatSymbolsZoneStringsCoreTest {
    private fun blank() = DateFormatSymbolsStateCore(DateFormatSymbolsDataCore(object : DateFormatSymbolsDataCore.Owner {
        override fun className() = "test.Symbols"
        override fun currentTimeMillis() = 0L
        override fun defaultZoneOffset(time: Long) = 0
    })).also { it.data.validLocale = ULocale.US }

    @Test fun aliasesCallOrderAndFirstReturnIdentity() {
        val calls = arrayListOf<String>()
        var sharedTypes: Array<NameType>? = null
        val provider = object : DateFormatSymbolsZoneStringsCore.Names {
            override fun loadAllDisplayNames() { calls.add("load") }
            override fun getDisplayNames(id: String, types: Array<NameType>, date: Long, dest: Array<String?>, offset: Int) {
                calls.add("names:$id:$date:$offset")
                if (sharedTypes == null) sharedTypes = types else assertSame(sharedTypes, types)
                assertContentEquals(arrayOf(NameType.LONG_STANDARD, NameType.SHORT_STANDARD, NameType.LONG_DAYLIGHT, NameType.SHORT_DAYLIGHT), types)
                dest[offset] = id
            }
        }
        val core = DateFormatSymbolsZoneStringsCore({ calls.add("clock"); 123L }, { calls.add("factory:$it"); provider },
            { calls.add("ids"); arrayOf("Alias", "Unknown") }, { calls.add("canonical:$it"); if (it == "Alias") "Canonical" else null })
        val state = blank()
        val first = core.getZoneStrings(state)
        assertEquals(listOf("ids", "factory:en_US", "load", "clock", "canonical:Alias", "names:Canonical:123:1", "canonical:Unknown", "names:Unknown:123:1"), calls)
        assertSame(first, state.storedZoneStrings)
        assertEquals("Alias", first[0]!![0]); assertEquals("Canonical", first[0]!![1])
        first[0]!![1] = "mutated"
        val second = core.getZoneStrings(state)
        assertNotSame(first, second); assertNotSame(first[0], second[0]); assertEquals("mutated", second[0]!![1])
        assertEquals(8, calls.size)
    }

    @Test fun failedGenerationIsRetriedAndCustomTablesBypassOwners() {
        var fail = true; var loads = 0
        val provider = object : DateFormatSymbolsZoneStringsCore.Names {
            override fun loadAllDisplayNames() { loads++ }
            override fun getDisplayNames(id: String, types: Array<NameType>, date: Long, dest: Array<String?>, offset: Int) {
                if (id == "B" && fail) error("failed second row")
                dest[1] = id
            }
        }
        val core = DateFormatSymbolsZoneStringsCore({ 0 }, { provider }, { arrayOf("A", "B") }, { it })
        val state = blank()
        assertFailsWith<IllegalStateException> { core.getZoneStrings(state) }; assertNull(state.storedZoneStrings)
        fail = false; assertEquals(2, core.getZoneStrings(state).size); assertEquals(2, loads)
        state.setZoneStrings(arrayOf(arrayOf("custom")))
        val bypass = DateFormatSymbolsZoneStringsCore({ error("clock") }, { error("names") }, { error("ids") })
        assertEquals("custom", bypass.getZoneStrings(state)[0]!![0])
    }
}
