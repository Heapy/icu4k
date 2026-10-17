// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*
import io.github.heapy.icu4k.impl.IllegalIcuArgumentException

class TransliteratorParserTest {
    private val rules=listOf("a > B; b > CC;","x{ab}y > z;","^a$ > z;","(a)(b) > $"+"2$"+"1;","[ab]+ > Q;","'ab'?c > Z;","$"+"v=[a-c]; ($"+"v+) > '<'$"+"1'>';","a > |@@b;","a > b@@|;","a <> b;","::Null; a>b; ::Null; c>d;","::[a-z]; a>b;","a>b; ::([a-z]);","a > &Null(bc);","use variable range 0xE000 0xE100; [a]>b;","use maximum backup 2;","use nfd rules;","a > [b];","$"+"bad > x;","a > $"+"1;","* > x;","::   ","a > b; a > c;",". > X;","\\u0061 > \\U0001F600;","a > 'bad;")
    // Captured from ICU4J 78.3 alone: parse errors/state, rules and engine output/positions.
    private val expected=intArrayOf(1349561881,1213138767,872031881,-1583120777,-194948999,353620313,1888181696,396837077,504840277,-111851791,239904837,497076017,690136017,-606188569,-564884729,2044441779,-1839882451,-926307019,-1870758171,337371767,166669937,-518328805,1973961462,-1711138035,-552764646,-1997348275)
    private val factory=TransliterationFactory { basic,canonical ->
        if(basic!="Any-Null")null else object: TransliterationFunction {
            override fun getID()=canonical ?: basic
            override fun setFilter(filter: UnicodeSet){}
            override fun getTargetSet()=UnicodeSet()
            override fun transliterate(text: Replaceable,start: Int,limit: Int)=limit
        }
    }
    @Test fun literalRules()=verify(0)
    @Test fun contextRules()=verify(1)
    @Test fun anchoredRules()=verify(2)
    @Test fun segments()=verify(3)
    @Test fun quantifiedSets()=verify(4)
    @Test fun quantifiedQuotes()=verify(5)
    @Test fun variablesAndSegments()=verify(6)
    @Test fun negativeCursorOffset()=verify(7)
    @Test fun positiveCursorOffset()=verify(8)
    @Test fun bidirectionalRules()=verify(9)
    @Test fun idBlocks()=verify(10)
    @Test fun forwardGlobalFilter()=verify(11)
    @Test fun reverseGlobalFilter()=verify(12)
    @Test fun functionReplacement()=verify(13)
    @Test fun variableRangePragma()=verify(14)
    @Test fun unsupportedSourceBackupPragma()=verify(15)
    @Test fun unsupportedSourceNormalizationPragma()=verify(16)
    @Test fun invalidOutputSet()=verify(17)
    @Test fun undefinedVariable()=verify(18)
    @Test fun undefinedSegment()=verify(19)
    @Test fun misplacedQuantifier()=verify(20)
    @Test fun truncatedId()=verify(21)
    @Test fun maskingError()=verify(22)
    @Test fun dot()=verify(23)
    @Test fun escapes()=verify(24)
    @Test fun unterminatedQuote()=verify(25)
    private fun verify(k: Int) {
        TransliteratorIDParser.registerSpecialInverse("Null","Null",false)
        var hash=0x811c9dc5.toInt()
        fun add(v: Int){hash=(hash xor v)*16777619}
        fun string(s: String?){if(s==null){add(-1);return};add(s.length);for(c in s)add(c.code)}
        fun error(e: Throwable?) {
            if(e==null){add(0);return}
            var current: Throwable?=e;var count=0
            while(current!=null && count<50) {
                val t=current
                string(if(t is IndexOutOfBoundsException)"bounds" else (when(t){is IllegalIcuArgumentException->"IllegalIcuArgumentException";is IllegalArgumentException->"IllegalArgumentException";is NullPointerException->"NullPointerException";else->"RuntimeException"})+":"+t.message)
                ++count;current=t.cause
            }
            add(count)
        }
        val texts=listOf("","abc","xaby","aabbcc","😀abc","ab","xabc")
        for(dir in 0..1) {
            val parser=TransliteratorParser(factory)
            var failure: Throwable?=null
            try{parser.parse(rules[k],dir)}catch(e: Throwable){failure=e}
            error(failure);string(parser.compoundFilter?.toPattern(false))
            add(parser.idBlockVector.size);for(id in parser.idBlockVector)string(id)
            add(parser.dataVector.size)
            for(data in parser.dataVector) {
                add(data.variablesBase.code);add(data.variables!!.size)
                val keys=data.variableNames.keys.sorted();add(keys.size)
                for(key in keys){string(key);string(data.variableNames[key]!!.concatToString())}
                for(escape in listOf(false,true))string(data.ruleSet.toRules(escape))
                if(failure==null)for(value in texts)for(incremental in listOf(false,true)) {
                    val text=ReplaceableString(value);val pos=TransliterationPosition(0,value.length,0,value.length)
                    var thrown: Throwable?=null
                    try{data.ruleSet.handleTransliterate(text,pos,incremental)}catch(e: Throwable){thrown=e}
                    error(thrown);string(text.toString());add(pos.contextStart);add(pos.contextLimit);add(pos.start);add(pos.limit)
                }
            }
        }
        assertEquals(expected[k],hash,"rule=${rules[k]}")
    }
    @Test fun lineContinuationAndErrorChain() {
        val parser=TransliteratorParser(factory)
        parser.parseLines(arrayOf("a > \\","b;","c > d;"),0)
        assertEquals("a > b;\nc > d;",parser.dataVector.single().ruleSet.toRules(false))
        val ex=assertFailsWith<IllegalIcuArgumentException>{parser.parse("!>!;".repeat(35),0)}
        var count=0;var cause: Throwable?=ex
        while(cause!=null){++count;cause=cause.cause}
        assertEquals(32,count)
    }
    @Test fun wrappedRuntimeCauseChain() {
        val parser=TransliteratorParser(factory)
        val ex=assertFailsWith<IllegalIcuArgumentException>{parser.parse("* > x; ! > !;",0)}
        assertTrue(ex.cause is IndexOutOfBoundsException)
        assertTrue(ex.cause!!.cause is IllegalIcuArgumentException)
    }

}
