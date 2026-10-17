package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ParseException
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

internal class MessageParseFixture(pattern: String) : MessageFormatParseCore.Owner {
    override val messagePattern = MessagePattern(pattern)
    override var cachedParsers: MutableMap<Int, MessageFormatParseCore.Parser?>? = null
    val core = MessageFormatParseCore(this)
    fun install(ordinal: Int, parser: MessageFormatParseCore.Parser?) {
        var index = 0
        repeat(ordinal + 1) { index = MessageFormatPatternCore.nextTopLevelArgStart(messagePattern,index) }
        if (cachedParsers == null) cachedParsers = HashMap()
        cachedParsers!![index] = parser
    }
}
class MessageFormatParseCoreTest {
    @Test fun arraySlotsRepeatedArgumentsAndPreservedErrorIndex() {
        val fixture=MessageParseFixture("A {2} / {0}.")
        val pos=ParsePosition(0).also { it.errorIndex=7 }
        assertContentEquals(arrayOf("right",null,"left"),fixture.core.parse("A left / right.",pos))
        assertEquals(15,pos.index);assertEquals(7,pos.errorIndex)
        val named=MessageParseFixture("{who}/{who}")
        assertEquals<Map<String,Any?>?>(mapOf("who" to "A"),named.core.parseToMap("A/{who}",ParsePosition(0)))
        assertFailsWith<IllegalArgumentException> { named.core.parse("A/B",ParsePosition(0)) }
    }
    @Test fun customNumberParserAndExplicitNullOverride() {
        val fixture=MessageParseFixture("{0,plural,one{one} other{other}}!")
        assertFailsWith<UnsupportedOperationException> { fixture.core.parse("other!",ParsePosition(0)) }
        fixture.install(0,null)
        assertContentEquals(arrayOf("other"),fixture.core.parse("other!",ParsePosition(0)))
        val nf=NumberFormat.getInstance(ULocale("en_US"))!!
        val numbers=MessageParseFixture("month={0}")
        numbers.install(0,MessageFormatParseCore.Parser { text,pos -> nf.parse(text,pos) })
        assertContentEquals(arrayOf(12L),numbers.core.parse("month=12",ParsePosition(0)))
    }
    @Test fun choiceMatchesLongestLiteralAndDoesNotRecursivelyParseArguments() {
        val fixture=MessageParseFixture("{0,choice,0#no|1#one|2<many {1}}!")
        assertContentEquals(arrayOf(2.0),fixture.core.parse("many {1}!",ParsePosition(0)))
        assertNull(fixture.core.parse("many value!",ParsePosition(0)))
        val pattern=MessagePattern().parseChoiceStyle("0#first|1#second|2<third")
        fun selected(n: Double): String {
            val start=MessageFormatPatternCore.findChoiceSubMessage(pattern,0,n)
            return pattern.getPatternString()!!.substring(pattern.getPart(start).getLimit(),pattern.getPart(pattern.getLimitPartIndex(start)).getIndex())
        }
        assertEquals("first",selected(Double.NaN));assertEquals("second",selected(2.0));assertEquals("third",selected(2.1))
    }
    @Test fun noProgressBoundsAndThrowingParseMethods() {
        val fixture=MessageParseFixture("{0}")
        assertNull(fixture.core.parse(null,ParsePosition(0)))
        assertNull(fixture.core.parse("",ParsePosition(0)))
        assertFailsWith<ParseException> { fixture.core.parse("") }
        assertFailsWith<ParseException> { fixture.core.parseToMap(null) }
        assertFailsWith<IndexOutOfBoundsException> { fixture.core.parse("value",ParsePosition(-1)) }
        val pos=ParsePosition(0)
        assertNull(MessageParseFixture("prefix {0}").core.parse("wrong",pos))
        assertEquals(0,pos.index);assertEquals(0,pos.errorIndex)
    }
    @Test fun choiceQuoteChunkMatchingKeepsSourceOffsetQuirk() {
        val fixture=MessageParseFixture("{0,choice,0#ab''cd|1#other}")
        val pos=ParsePosition(0)
        assertNull(fixture.core.parse("ab'cd",pos))
        assertEquals(0,pos.errorIndex)
    }
}
