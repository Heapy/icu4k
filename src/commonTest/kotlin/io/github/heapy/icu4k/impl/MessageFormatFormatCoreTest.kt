package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import kotlin.test.*

class MessageFormatFormatCoreTest {
    private fun text(iterator: FormatAttributedCharacterIterator): String=buildString {
        var c=iterator.first()
        while(c!=FORMAT_CHARACTER_DONE) {append(c);c=iterator.next()}
    }
    @Test fun inferredNumbersMissingAndNullArguments() {
        val f=MessageFormatFormatFixture("{0}: {1}; {2}")
        assertEquals("1,234: null; {2}",f.text(arrayOf<Any?>(1234,null)))
        assertEquals("1,234: value; {2}",f.text(mapOf("0" to 1234,"1" to "value")))
        assertEquals("{0}: {1}; {2}",f.text(null))
        assertFailsWith<IllegalArgumentException> {MessageFormatFormatFixture("{name}").text(arrayOf<Any?>("x"))}
    }
    @Test fun pluralOffsetsNestedSelectAndChoice() {
        val f=MessageFormatFormatFixture("{n,plural,offset:1 =0{none} one{# friend} other{# friends and {who,select,a{Alice} other{someone}}}}")
        assertEquals("none",f.text(mapOf("n" to 0)))
        assertEquals("1 friend",f.text(mapOf("n" to 2)))
        assertEquals("4 friends and Alice",f.text(mapOf("n" to 5,"who" to "a")))
        assertEquals("many X",MessageFormatFormatFixture("{0,choice,0#none|1#one|1<many {1}}").text(arrayOf<Any?>(3,"X")))
    }
    @Test fun pluralCategoryUsesFormattedPrecisionInOtherBranch() {
        val f=MessageFormatFormatFixture("{n,plural,one{ONE} other{OTHER {n,number,0.0}}}")
        assertEquals("OTHER 1.0",f.text(mapOf("n" to 1)))
        assertEquals("ONE",MessageFormatFormatFixture("{n,plural,one{ONE} other{OTHER #}}").text(mapOf("n" to 1)))
        assertEquals("22nd",MessageFormatFormatFixture("{0,selectordinal,one{#st} two{#nd} few{#rd} other{#th}}").text(arrayOf<Any?>(22)))
    }
    @Test fun argumentSpansAndFirstArgumentPosition() {
        val f=MessageFormatFormatFixture("A {0,number,integer} / {1}")
        val dest=MessageFormatFormatCore.Destination(StringBuilder("p:"))
        val fp=FormatFieldPosition(MessageArgumentField).also { it.setBeginIndex(7);it.setEndIndex(9) }
        f.core.format(arrayOf<Any?>(1234,"X"),dest,fp)
        assertEquals("p:A 1,234 / X",dest.output.toString())
        assertEquals(4,fp.getBeginIndex());assertEquals(9,fp.getEndIndex())
        val it=f.core.iterator(arrayOf<Any?>(1234,"X"))
        assertEquals("A 1,234 / X",text(it))
        it.setIndex(2);assertEquals(0,it.getAttribute(MessageArgumentField));assertNotNull(it.getAttribute(NumberFormat.Field.INTEGER))
        it.setIndex(10);assertEquals(1,it.getAttribute(MessageArgumentField))
        assertFailsWith<NullPointerException> {f.core.iterator(null)}
    }
    @Test fun complexCustomFormatsReparseNestedArguments() {
        val f=MessageFormatFormatFixture("{0}")
        val formatter=object:MessageFormatFormatCore.Formatter {
            override val complex=true
            override fun format(value:Any?):String="selected {1}"
            override fun iterator(value:Any?):FormatAttributedCharacterIterator=attributedCharacterIterator(format(value),emptyList())
        }
        f.cachedFormatters[1]=formatter
        assertEquals("selected X",f.text(arrayOf<Any?>(1,"X")))
        f.cachedFormatters[1]=null
        assertEquals("1",f.text(arrayOf<Any?>(1,"X")))
    }
    @Test fun genericAttributesAndNestedArgumentOverlays() {
        val key=object:FormatAttribute("custom") {}
        val f=MessageFormatFormatFixture("{0}")
        f.cachedFormatters[1]=object:MessageFormatFormatCore.Formatter {
            override fun format(value:Any?)="X"
            override fun iterator(value:Any?)=attributedCharacterIterator("X",listOf(FormatAttributeRange(key,"value",0,1)))
        }
        val it=f.core.iterator(arrayOf<Any?>(1))
        it.first();assertEquals("value",it.getAttribute(key));assertEquals(0,it.getAttribute(MessageArgumentField))
        val nested=MessageFormatFormatFixture("{0,plural,other{# {1}}}").core.iterator(arrayOf<Any?>(2,"X"))
        assertEquals("2 X",text(nested))
        nested.setIndex(0);assertNotNull(nested.getAttribute(NumberFormat.Field.INTEGER));assertEquals(0,nested.getAttribute(MessageArgumentField))
        nested.setIndex(2);assertEquals(0,nested.getAttribute(MessageArgumentField))
    }
    @Test fun assertionsAreLazyAndExplicitSelectorsBypassPluralRules() {
        val f=object:MessageFormatFormatFixture("{0,plural,=0{exact #} one{one} other{other #}}") {
            override val assertionsEnabled=true
        }
        assertEquals("exact 0",f.text(arrayOf<Any?>(0)))
        assertFailsWith<AssertionError> {f.text(arrayOf<Any?>(Double.NaN))}
        assertEquals("other NaN",MessageFormatFormatFixture("{0,plural,one{one} other{other #}}").text(arrayOf<Any?>(Double.NaN)))
    }
}
