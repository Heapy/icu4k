// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.MissingResourceException
import kotlin.test.*

class RbnfCapitalizationCoreTest {
    private class Parent {
        var value = DisplayContext.CAPITALIZATION_NONE
        val events = ArrayList<String>()
        fun set(value: DisplayContext?) {
            events.add("parent:$value")
            if (value!!.type() == DisplayContext.Type.CAPITALIZATION) this.value = value
        }
    }
    @Test fun initializationOrderShortVectorsAndContextFlags() {
        for (flags in listOf<IntArray?>(null, intArrayOf(), intArrayOf(1), intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(1, 2, 3))) {
            val parent = Parent()
            var loads = 0
            var iterators = 0
            val core = RbnfCapitalizationCore(ULocale.ENGLISH, {
                parent.events.add("load"); ++loads; flags
            }, { parent.events.add("iterator"); ++iterators; BreakIterator.getSentenceInstance(it) })
            core.setContext(DisplayContext.STANDARD_NAMES, parent::set)
            core.setContext(DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE, parent::set)
            assertEquals(0, loads); assertEquals(0, iterators)
            core.setContext(DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, parent::set)
            assertEquals(1, loads)
            assertEquals("parent:CAPITALIZATION_FOR_UI_LIST_OR_MENU", parent.events[2])
            assertEquals("load", parent.events[3])
            val list = flags != null && flags.size >= 2 && flags[0] != 0
            val standalone = flags != null && flags.size >= 2 && flags[1] != 0
            assertEquals(if (list) "One! Two" else "one! two", core.adjustForContext("one! two") { parent.value })
            core.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE, parent::set)
            assertEquals(if (standalone) "One! Two" else "one! two", core.adjustForContext("one! two") { parent.value })
            assertEquals(1, loads)
            assertEquals(if (list || standalone) 1 else 0, iterators)
            core.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, parent::set)
            assertEquals(1, iterators)
            assertEquals("One! Two", core.adjustForContext("one! two") { parent.value })
            core.setContext(DisplayContext.CAPITALIZATION_NONE, parent::set)
            assertEquals("one! two", core.adjustForContext("one! two") { parent.value })
        }
    }
    @Test fun failedLookupAndIteratorCreationRetainSourcePartialState() {
        val parent = Parent()
        var loads = 0
        var creations = 0
        val core = RbnfCapitalizationCore(ULocale.ENGLISH, {
            if (++loads == 1) throw MissingResourceException("missing bundle", "bundle", "")
            intArrayOf(1, 0)
        }, {
            if (++creations == 1) throw IllegalStateException("iterator failed")
            BreakIterator.getSentenceInstance(it)
        })
        assertFailsWith<MissingResourceException> { core.setContext(DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, parent::set) }
        assertEquals(DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, parent.value)
        assertEquals(1, loads)
        assertFailsWith<IllegalStateException> { core.setContext(DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, parent::set) }
        assertEquals(2, loads)
        core.setContext(DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU, parent::set)
        assertEquals(2, loads) // Info was already published before the failed iterator factory.
        assertEquals(2, creations)
        assertEquals("One", core.adjustForContext("one") { parent.value })
        assertFailsWith<NullPointerException> { core.setContext(null, parent::set) }
        assertEquals(2, loads)
    }
    @Test fun firstCodePointGateAndLazyVirtualContext() {
        var iterators = 0
        val core = RbnfCapitalizationCore(ULocale.ENGLISH, { error("not requested") }, { ++iterators; BreakIterator.getSentenceInstance(it) })
        var reads = 0
        fun adjust(text: String?, context: DisplayContext? = DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE): String? =
            core.adjustForContext(text) { ++reads; context }
        assertNull(adjust(null)); assertEquals("", adjust("")); assertEquals(" one", adjust(" one"))
        assertEquals("1 one", adjust("1 one")); assertEquals("One! two", adjust("One! two"))
        assertEquals("ǅungla", adjust("ǅungla")); assertEquals("ʰello", adjust("ʰello")) // Lowercase property alone does not pass the GC=Ll gate.
        assertEquals(0, iterators)
        assertEquals("One! TWO", adjust("one! TWO")) // No lowercasing of the rest of either sentence.
        assertEquals(1, iterators)
        assertEquals("\uD801\uDC00abc", adjust("\uD801\uDC28abc"))
        assertEquals("one", adjust("one", null))
        assertEquals("one", adjust("one", DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE))
        assertEquals(11, reads)
        assertFailsWith<IllegalStateException> { core.adjustForContext(null) { error("context read first") } }
    }
    @Test fun localeSensitiveTitlecaseAndShallowIteratorCopy() {
        for ((id, original, expected) in listOf(Triple("tr", "istanbul", "İstanbul"), Triple("nl", "ijsselmeer", "IJsselmeer"), Triple("de", "ßeta", "Sseta"))) {
            val core = RbnfCapitalizationCore(ULocale(id))
            core.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE) {}
            assertEquals(expected, core.adjustForContext(original) { DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE })
        }
        var created = 0
        val iterator = BreakIterator.getSentenceInstance(ULocale.ENGLISH)
        val core = RbnfCapitalizationCore(ULocale.ENGLISH, { intArrayOf(1, 1) }, { ++created; iterator })
        core.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE) {}
        val clone = core.copy()
        assertEquals("One", clone.adjustForContext("one") { DisplayContext.CAPITALIZATION_FOR_STANDALONE })
        assertEquals("one", iterator.getText().let { text -> buildString { var c = text.first(); while (c != CharacterIterator.DONE) { append(c); c = text.next() } } })
        assertEquals(1, created)
        core.adjustForContext("two") { DisplayContext.CAPITALIZATION_FOR_STANDALONE }
        assertEquals("two", iterator.getText().let { text -> buildString { var c = text.first(); while (c != CharacterIterator.DONE) { append(c); c = text.next() } } })
        assertEquals(1, created)
    }
    @Test fun formatterOwnerConnectsContextAndCopiesItsState() {
        val f = RbnfFormatterCore.fromDescription("%main: 0: one! two;", locale = ULocale.ENGLISH, scannerProviderFactory = { error("unused") })
        val parent = Parent()
        f.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE, parent::set)
        assertEquals("One! Two", f.adjustForContext(f.format(0L)) { parent.value })
        val clone = f.copy()
        assertEquals("One! Two", clone.adjustForContext(clone.format(0L)) { parent.value })
    }
}
