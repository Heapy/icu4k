package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.PatternTokenizer as Source
import com.ibm.icu.text.UnicodeSet as SourceSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.random.Random
import kotlin.test.*

class PatternTokenizerOracleTest {
    private fun outcome(action: ()->Any?): Any? = try { action() } catch (e: Exception) {
        (if(e is IndexOutOfBoundsException) "bounds" else e.javaClass.name) to if(e is NullPointerException || e is IndexOutOfBoundsException) null else e.message
    }
    private fun state(p: Any): List<Any?> = listOf("start","limit","pattern","usingQuote","usingSlash","needingQuoteCharacters").map { name ->
        val value=p.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(p)
        when(value) { is SourceSet -> value.toPattern(false); is UnicodeSet -> value.toPattern(false); else -> value }
    }
    @Test fun quotingNormalizationAndRawUtf16MatchSource() {
        val random=Random(783)
        val alphabet="aabb Hm{} :/'\\uU019AaFxyz\u0000\uffff😀\ud800\udfff"
        val values=listOf("","'","''","\\","\\u","\\u0041","\\U0001F600","\\u12x4","\\U00110000","\\UFFFFFFFF","'broken","a''b","a\\qH")+List(2000) { buildString { repeat(random.nextInt(50)) { append(alphabet[random.nextInt(alphabet.length)]) } } }
        for(quote in listOf(false,true)) for(slash in listOf(false,true)) for(escape in listOf("[]","[^\\u0020-\\u007E]")) {
            val a=Source().setSyntaxCharacters(SourceSet("[a-zA-Z{}]")).setIgnorableCharacters(SourceSet("[ \\t]")).setExtraQuotingCharacters(SourceSet("[аб😀]")).setEscapeCharacters(SourceSet(escape)).setUsingQuote(quote).setUsingSlash(slash)
            val b=PatternTokenizer().setSyntaxCharacters(UnicodeSet("[a-zA-Z{}]")).setIgnorableCharacters(UnicodeSet("[ \\t]")).setExtraQuotingCharacters(UnicodeSet("[аб😀]")).setEscapeCharacters(UnicodeSet(escape)).setUsingQuote(quote).setUsingSlash(slash)
            for(value in values) {
                assertEquals(outcome { a.quoteLiteral(value) },outcome { b.quoteLiteral(value) },"quote/$quote/$slash/$escape/$value")
                a.setPattern(value); b.setPattern(value)
                assertEquals(outcome { a.normalize() },outcome { b.normalize() },"normalize/$quote/$slash/$escape/$value")
                assertEquals(state(a),state(b),"state/$value")
            }
        }
    }
    @Test fun tokenStatusesBufferAppendsInvalidBoundsAndNullBuffers() {
        val random=Random(13183)
        val alphabet="a '\\uU12xz😀"
        for(value in listOf("","a:'x'","\\u12x4x","\\U00110000","😀a","\ud800x","x\udfff","'x''z'")+List(1000) { buildString { repeat(random.nextInt(25)) { append(alphabet[random.nextInt(alphabet.length)]) } } }) {
            val a=Source().setSyntaxCharacters(SourceSet("[a-z😀]")).setUsingQuote(true)
            val b=PatternTokenizer().setSyntaxCharacters(UnicodeSet("[a-z😀]")).setUsingQuote(true)
            for(start in listOf(-1,0,1,value.length,value.length+1)) for(limit in listOf(-1,0,value.length,value.length+1)) for(nullBuffer in listOf(false,true)) {
                a.setPattern(value).setStart(start).setLimit(limit); b.setPattern(value).setStart(start).setLimit(limit)
                val ab=if(nullBuffer) null else StringBuffer("prefix"); val bb=if(nullBuffer) null else StringBuilder("prefix")
                assertEquals(outcome { a.next(ab) },outcome { b.next(bb) },"next/$value/$start/$limit/$nullBuffer")
                assertEquals(ab?.toString(),bb?.toString()); assertEquals(state(a),state(b))
            }
        }
    }
    @Test fun settingsClonesCacheInvalidationAndOverloadNulls() {
        val a=Source(); val b=PatternTokenizer()
        for(frozen in listOf(false,true)) {
            val sa=SourceSet("[ab]").let { if(frozen) it.freeze() else it }; val sb=UnicodeSet("[ab]").let { if(frozen) it.freeze() else it }
            a.setSyntaxCharacters(sa); b.setSyntaxCharacters(sb)
            assertEquals(a.getSyntaxCharacters()===sa,b.getSyntaxCharacters()===sb)
            a.quoteLiteral("a"); b.quoteLiteral("a"); assertEquals(state(a),state(b))
            assertEquals(outcome { a.setSyntaxCharacters(null) },outcome { b.setSyntaxCharacters(null) }); assertEquals(state(a),state(b))
            a.setExtraQuotingCharacters(SourceSet("[c]")); b.setExtraQuotingCharacters(UnicodeSet("[c]")); assertEquals(state(a),state(b))
            assertEquals(a.quoteLiteral("c"),b.quoteLiteral("c"))
            a.setEscapeCharacters(SourceSet("[a]")); b.setEscapeCharacters(UnicodeSet("[a]")); assertEquals(state(a),state(b))
            assertEquals(a.quoteLiteral("a"),b.quoteLiteral("a"))
            if(!frozen) { sa.add('z'.code); sb.add('z'.code); assertEquals(a.getSyntaxCharacters().toPattern(false),b.getSyntaxCharacters().toPattern(false)) }
        }
        assertEquals(outcome { a.setPattern(null as String?) },outcome { b.setPattern(null as String?) })
        assertEquals(outcome { a.setPattern(null as CharSequence?) },outcome { b.setPattern(null as CharSequence?) })
        assertEquals(outcome { a.quoteLiteral(null as String?) },outcome { b.quoteLiteral(null as String?) }); assertEquals(state(a),state(b))
    }
    @Test fun normalizeVirtualDispatchAndFailureDoesNotRestoreStart() {
        val sourceTrace=ArrayList<String>(); val targetTrace=ArrayList<String>()
        val a=object: Source() {
            override fun next(buffer: StringBuffer?): Int { sourceTrace.add("next:${getStart()}"); if(getStart()==1) { setStart(7); error("stop") }; return super.next(buffer) }
            override fun quoteLiteral(value: CharSequence?): String { sourceTrace.add("chars:$value"); return super.quoteLiteral(value) }
            override fun quoteLiteral(value: String?): String { sourceTrace.add("string:$value"); return super.quoteLiteral(value) }
        }.setSyntaxCharacters(SourceSet("[a]")).setPattern("ax")
        val b=object: PatternTokenizer() {
            override fun next(buffer: StringBuilder?): Int { targetTrace.add("next:${getStart()}"); if(getStart()==1) { setStart(7); error("stop") }; return super.next(buffer) }
            override fun quoteLiteral(value: CharSequence?): String { targetTrace.add("chars:$value"); return super.quoteLiteral(value) }
            override fun quoteLiteral(value: String?): String { targetTrace.add("string:$value"); return super.quoteLiteral(value) }
        }.setSyntaxCharacters(UnicodeSet("[a]")).setPattern("ax")
        assertEquals(outcome { a.normalize() },outcome { b.normalize() }); assertEquals(sourceTrace,targetTrace); assertEquals(a.getStart(),b.getStart()); assertEquals(7,b.getStart())
    }
    @Test fun utf16AppendValidatesCodePointBeforeNullableTarget() {
        for(cp in listOf(Int.MIN_VALUE,-1,0,0xd800,0x10000,0x10ffff,0x110000,Int.MAX_VALUE)) {
            assertEquals(outcome { com.ibm.icu.text.UTF16.append(null as StringBuffer?,cp) },outcome { io.github.heapy.icu4k.text.UTF16.append(null,cp) })
            assertEquals(outcome { com.ibm.icu.text.UTF16.appendCodePoint(null as StringBuffer?,cp) },outcome { io.github.heapy.icu4k.text.UTF16.appendCodePoint(null,cp) })
        }
    }

}
