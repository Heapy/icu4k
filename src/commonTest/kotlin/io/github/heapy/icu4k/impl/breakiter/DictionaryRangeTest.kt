package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.StringCharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class DictionaryRangeTest {
    @Test fun copiesHandledCharactersAndRestoresCursorAfterDispatch() {
        var seen = emptyList<Int>()
        val engine = object : DictionaryBreakEngine() {
            override fun divideUpDictionaryRange(text: CharacterIterator, rangeStart: Int, rangeEnd: Int, foundBreaks: DequeI, isPhraseBreaking: Boolean): Int {
                seen = listOf(rangeStart, rangeEnd, text.getIndex())
                assertTrue(isPhraseBreaking)
                foundBreaks.push(rangeEnd)
                text.first()
                return 1
            }
        }
        val characters = UnicodeSet(65, 90).add(0x1f600)
        engine.setCharacters(characters)
        characters.clear()
        assertTrue(engine.handles(65)); assertFalse(engine.handles(97))
        val text = StringCharacterIterator("xA😀B y", 1)
        val found = DictionaryBreakEngine.DequeI().apply { push(-1) }
        assertEquals(1, engine.findBreaks(text, 0, text.getEndIndex(), found, true))
        assertEquals(listOf(1, 5, 5), seen)
        assertEquals(5, text.getIndex())
        assertEquals(2, found.size()); assertEquals(-1, found.elementAt(0)); assertEquals(5, found.elementAt(1))
    }
}
