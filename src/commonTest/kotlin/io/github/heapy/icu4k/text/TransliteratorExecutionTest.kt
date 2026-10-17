// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

internal class TransliterationExecutionFixture(rule:String,id:String="test",filter:String?=null) {
    val data=TransliteratorParser(TransliterationFactory{_,_->null}).apply{parse(rule,0)}.dataVector.single()
    val state=object:TransliteratorExecutionCore.Input {
        override val id=id;override var filter:UnicodeSet?=null
        override fun handleTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean)=data.ruleSet.handleTransliterate(text,pos,incremental)
    }
    val engine=TransliteratorExecutionCore(state)
    init{engine.setFilter(filter?.let(::UnicodeSet))}
}
class TransliteratorExecutionTest {
    private val rules=listOf("ab > X;","a > AA; b > ;","(ab) > '<'$"+"1'>';","x{ab}y > Q;","[ab]+ > Z;","a > b|c;",". > X;","😀 > QQ;")
    // ICU4J 78.3 public API: batch results/errors and every incremental text/position.
    private val expected=intArrayOf(-1296560884,-1722097027,1366177330,1658095972,745278838,99350466,-889661911,1571621587)
    @Test fun partialKey()=verify(0)
    @Test fun growthAndDeletion()=verify(1)
    @Test fun captures()=verify(2)
    @Test fun context()=verify(3)
    @Test fun quantifier()=verify(4)
    @Test fun cursor()=verify(5)
    @Test fun dot()=verify(6)
    @Test fun supplementary()=verify(7)
    private fun verify(k:Int) {
        var hash=0x811c9dc5.toInt()
        fun add(v:Int){hash=(hash xor v)*16777619}
        fun string(s:String){add(s.length);for(c in s)add(c.code)}
        fun error(e:Throwable?){when(e){null->add(0);is IndexOutOfBoundsException->add(1);is NullPointerException->add(2);else->{add(if(e is IllegalArgumentException)3 else 4);string(e.message?:"null")}}}
        fun snap(t:ReplaceableString,p:TransliterationPosition){string(t.toString());add(p.contextStart);add(p.contextLimit);add(p.start);add(p.limit)}
        val values=listOf("","abc","xaby","aabbab","😀ab",charArrayOf('a','\ud800','b','\udc00').concatToString())
        for(filter in listOf(null,"[a]","[ab]","[a-z😀]","[]")) {
            val engine=TransliterationExecutionFixture(rules[k],filter=filter).engine
            for(value in values) {
                for(start in -1..value.length)for(limit in maxOf(0,start)..value.length+1) {
                    val text=ReplaceableString(value)
                    try{add(engine.transliterate(text,start,limit));error(null)}catch(e:Throwable){error(e)}
                    string(text.toString())
                }
                val text=ReplaceableString();val pos=TransliterationPosition()
                for(c in value) {
                    try{engine.transliterate(text,pos,c.toString());error(null)}catch(e:Throwable){error(e)};snap(text,pos)
                    try{engine.transliterate(text,pos,null);error(null)}catch(e:Throwable){error(e)};snap(text,pos)
                }
                try{engine.finishTransliteration(text,pos);error(null)}catch(e:Throwable){error(e)};snap(text,pos)
            }
        }
        assertEquals(expected[k],hash)
    }
    @Test fun splitSurrogateAndRollbackVisibility() {
        val engine=TransliterationExecutionFixture("😀 > QQ; ab > X;").engine
        val text=ReplaceableString();val pos=TransliterationPosition()
        engine.transliterate(text,pos,"a")
        assertEquals("a",text.toString());assertEquals(0,pos.start)
        engine.transliterate(text,pos,"b")
        assertEquals("X",text.toString());assertEquals(1,pos.start)
        engine.transliterate(text,pos,charArrayOf('\ud83d').concatToString())
        assertEquals(1,pos.start);assertEquals(2,pos.limit)
        engine.transliterate(text,pos,charArrayOf('\ude00').concatToString())
        assertEquals("XQQ",text.toString());assertEquals(3,pos.start)
    }
}
