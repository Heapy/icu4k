package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class PatternTokenizerTest {
    private fun tokenizer()=PatternTokenizer().setSyntaxCharacters(UnicodeSet("[a-zA-Z]")).setUsingQuote(true)
    private fun tokens(value: String): List<List<Any>> {
        val parser=tokenizer().setPattern(value)
        val result=ArrayList<List<Any>>()
        while(true) { val buffer=StringBuilder(); val status=parser.next(buffer); result.add(listOf(status,buffer.toString(),parser.getStart())); if(status==0) return result }
    }
    @Test fun standaloneIcu4jTokenSnapshotsAndEscapeRecovery() {
        assertEquals(listOf(listOf(1,"h",1),listOf(1,"h",2),listOf(2,":",5),listOf(1,"m",6),listOf(1,"m",7),listOf(2," o'clock!",24),listOf(0,"",24)),tokens("hh':'mm 'o''clock'\\u0021"))
        assertEquals(listOf(listOf(1,"a",1),listOf(4,"",5),listOf(1,"x",6),listOf(2,"4",7),listOf(1,"b",8),listOf(0,"",8)),tokens("a\\u12x4b"))
        assertEquals(listOf(listOf(3,"unterminated",13),listOf(0,"",13)),tokens("'unterminated"))
        assertEquals(listOf(listOf(2,"A",6),listOf(0,"",6)),tokens("\\u0041"))
        assertEquals(listOf(listOf(2,"😀",2),listOf(0,"",2)),tokens("😀"))
        assertEquals(listOf(listOf(2,"'",2),listOf(0,"",2)),tokens("''"))
    }
    @Test fun quotingFlagsEscapedSupplementaryAndSetOwnership() {
        val parser=tokenizer()
        assertEquals("'ab':'xy'",parser.quoteLiteral("ab:xy")); assertEquals("'a''''b'",parser.quoteLiteral("a''b"))
        parser.setUsingSlash(true); assertEquals("\\a\\b:\\x\\y",parser.quoteLiteral("ab:xy"))
        parser.setEscapeCharacters(UnicodeSet("[😀]")); assertEquals("\\U0001F600",parser.quoteLiteral("😀"))
        val mutable=UnicodeSet("[a]"); parser.setSyntaxCharacters(mutable); mutable.add('b'.code)
        assertFalse(parser.getSyntaxCharacters().contains('b'.code)); parser.getSyntaxCharacters().add('c'.code); assertFalse(parser.getSyntaxCharacters().contains('c'.code))
        val frozen=UnicodeSet("[x]").freeze(); parser.setSyntaxCharacters(frozen); assertSame(frozen,parser.getSyntaxCharacters())
        assertFailsWith<NullPointerException> { parser.setSyntaxCharacters(null) }; assertSame(frozen,parser.getSyntaxCharacters())
    }
    @Test fun explicitUtf16BoundsAndNullBuffersPreserveSourceOrder() {
        val parser=PatternTokenizer().setSyntaxCharacters(UnicodeSet("[a😀]")).setPattern("😀a").setLimit(1)
        val buffer=StringBuilder("prefix")
        assertEquals(1,parser.next(buffer)); assertEquals("prefix😀",buffer.toString()); assertEquals(2,parser.getStart()); assertEquals(0,parser.next(null))
        parser.setLimit(3).setStart(1); buffer.setLength(0)
        assertEquals(1,parser.next(buffer)); assertEquals("😀",buffer.toString()); assertEquals(3,parser.getStart())
        parser.setStart(-1); assertFailsWith<IndexOutOfBoundsException> { parser.next(buffer) }; assertEquals(-1,parser.getStart())
        parser.setStart(3).setLimit(4); assertFailsWith<IndexOutOfBoundsException> { parser.next(buffer) }; assertEquals(3,parser.getStart())
        parser.setPattern("\\U00110000")
        assertEquals("Illegal codepoint: 110000",assertFailsWith<IllegalArgumentException> { parser.next(null) }.message)
        assertEquals(0,parser.getStart())
        assertEquals("Illegal codepoint: ffffffff",assertFailsWith<IllegalArgumentException> { UTF16.appendCodePoint(null,-1) }.message)
        assertFailsWith<NullPointerException> { UTF16.append(null,0) }
        assertEquals(0,PatternTokenizer().next(null))
        assertEquals(5,PatternTokenizer().setIgnorableCharacters(UnicodeSet().add(32).add(9)).setPattern(" \t").next(null))
    }
    @Test fun normalizeRestoresStartOnlyOnSuccessAndUsesVirtualMethods() {
        val parser=tokenizer().setPattern("xxa':'b").setStart(2)
        assertEquals("a:b",parser.normalize()); assertEquals(2,parser.getStart())
        val trace=ArrayList<String>()
        val broken=object: PatternTokenizer() {
            override fun next(buffer: StringBuilder?): Int { trace.add("next:${getStart()}"); if(getStart()==1) { setStart(7); error("failure") }; return super.next(buffer) }
        }.setSyntaxCharacters(UnicodeSet("[a]")).setPattern("ax")
        assertFailsWith<IllegalStateException> { broken.normalize() }; assertEquals(7,broken.getStart()); assertEquals(listOf("next:0","next:1"),trace)
        val old=parser.getStart(); assertFailsWith<IllegalArgumentException> { parser.setPattern(null as String?) }; assertEquals(old,parser.getStart())
        assertFailsWith<NullPointerException> { parser.setPattern(null as CharSequence?) }; assertEquals(old,parser.getStart())
    }
}
