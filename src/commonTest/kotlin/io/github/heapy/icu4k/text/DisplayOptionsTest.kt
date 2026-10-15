package io.github.heapy.icu4k.text

import kotlin.test.*

class DisplayOptionsTest {
    @Test fun snapshotsNullAndIdentifiers() {
        val builder = DisplayOptions.builder()
        assertEquals(DisplayOptions.NounClass.UNDEFINED, builder.build().getNounClass())
        val snapshot = builder.setNounClass(DisplayOptions.NounClass.FEMININE).setGrammaticalCase(DisplayOptions.GrammaticalCase.DATIVE).build()
        builder.setNounClass(null)
        assertNull(builder.build().getNounClass())
        assertEquals(DisplayOptions.NounClass.FEMININE, snapshot.getNounClass())
        val copied = snapshot.copyToBuilder().setPluralCategory(DisplayOptions.PluralCategory.FEW).build()
        assertEquals(DisplayOptions.GrammaticalCase.DATIVE, copied.getGrammaticalCase())
        assertEquals(DisplayOptions.PluralCategory.UNDEFINED, snapshot.getPluralCategory())
        assertEquals(DisplayOptions.NounClass.UNDEFINED, DisplayOptions.NounClass.fromIdentifier(null))
        assertEquals(DisplayOptions.NounClass.UNDEFINED, DisplayOptions.NounClass.fromIdentifier("FEMININE"))
        assertEquals(DisplayOptions.GrammaticalCase.LOCATIVE_COPULATIVE, DisplayOptions.GrammaticalCase.fromIdentifier("locative_copulative"))
    }
    @Test fun enumListsRejectMutationIncludingNoOp() {
        val values = DisplayOptions.NounClass.VALUES as MutableList<DisplayOptions.NounClass>
        assertFailsWith<UnsupportedOperationException> { values.addAll(emptyList()) }
        assertFailsWith<UnsupportedOperationException> { values.remove(DisplayOptions.NounClass.FEMININE) }
        val iterator = values.listIterator(); iterator.next()
        assertFailsWith<UnsupportedOperationException> { iterator.set(DisplayOptions.NounClass.OTHER) }
        assertEquals(DisplayOptions.NounClass.entries.toList(), values)
    }
}
