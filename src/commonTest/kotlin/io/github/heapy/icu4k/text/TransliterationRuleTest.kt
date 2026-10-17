// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class TransliterationRuleTest {
    // Independent ICU4J 78.3 fingerprints: text, position, result and rule serialization.
    private val expected = intArrayOf(342414684,90671847,1353504452,1580988525,-1553748907,270392953,1021736125,1897065037,1253448761)
    @Test fun literalKey() = verify(0)
    @Test fun anteContext() = verify(1)
    @Test fun bothContexts() = verify(2)
    @Test fun supplementaryAnteContext() = verify(3)
    @Test fun supplementaryKey() = verify(4)
    @Test fun setKey() = verify(5)
    @Test fun setWithContexts() = verify(6)
    @Test fun emptyKey() = verify(7)
    @Test fun malformedUtf16() = verify(8)
    private fun verify(k: Int) {
        val inputs = listOf("a","ab","abc","😀a","a😀b","\ue000a","x\ue000y","",charArrayOf('\ud800','a').concatToString())
        val antes = intArrayOf(-1,1,1,2,1,0,1,-1,0)
        val posts = intArrayOf(-1,-1,2,-1,3,1,2,-1,-1)
        val texts = listOf("","a","ab","abc","xabcq","😀a","a😀b","x😀y","zaaz")
        val data = TransliterationData().apply { variablesBase='\ue000';variables=arrayOf(UnicodeSet("[a-z😀]")) }
        var hash = 0x811c9dc5.toInt()
        fun add(n: Int) { hash = (hash xor n) * 16777619 }
        fun string(s: String) { add(s.length); for(c in s) add(c.code) }
        for(anchor in 0..3) for(cursor in intArrayOf(-1,0,1)) for(offset in intArrayOf(-2,0,3)) {
            val rule = TransliterationRule(inputs[k],antes[k],posts[k],"X😀",cursor,offset,null,anchor and 1 != 0,anchor and 2 != 0,data)
            for(escape in listOf(false,true)) string(rule.toRule(escape))
            add(rule.getAnteContextLength());add(rule.getIndexValue())
            for(text in texts) for(start in 0..text.length) for(limit in start..text.length) for(incremental in listOf(false,true)) {
                val t=ReplaceableString(text);val p=TransliterationPosition(0,text.length,start,limit)
                try { add(rule.matchAndReplace(t,p,incremental)) }
                catch(e: Throwable) { add(when(e) { is IndexOutOfBoundsException -> -10; is NullPointerException -> -20; else -> -30 }) }
                string(t.toString());add(p.contextStart);add(p.contextLimit);add(p.start);add(p.limit)
            }
        }
        assertEquals(expected[k],hash)
    }
    @Test fun indexedRulesAndLoopGuard() {
        val data=TransliterationData().apply { variables=emptyArray() }
        val set=data.ruleSet
        set.addRule(TransliterationRule("a",-1,-1,"A",-1,0,null,false,false,data))
        set.addRule(TransliterationRule("b",-1,-1,"BB",-1,0,null,false,false,data))
        set.freeze()
        val text=ReplaceableString("abc😀ab")
        val pos=TransliterationPosition(0,text.length(),0,text.length())
        set.handleTransliterate(text,pos,false)
        assertEquals("ABBc😀ABB",text.toString())
        assertEquals(text.length(),pos.start)
        assertEquals(text.length(),pos.contextLimit)
        val cyclic=TransliterationRuleSet()
        cyclic.addRule(TransliterationRule("a",-1,-1,"a",0,0,null,false,false,data))
        cyclic.freeze()
        val stuck=TransliterationPosition(0,1,0,1)
        cyclic.handleTransliterate(ReplaceableString("a"),stuck,false)
        assertEquals(0,stuck.start)
    }
    @Test fun captureReplacementAndReset() {
        val data=TransliterationData().apply { variablesBase='\ue000';variables=emptyArray() }
        val segment=StringMatcher("ab",1,data)
        data.variables=arrayOf(segment)
        val rule=TransliterationRule("\ue000",-1,-1,"<\ue000>",-1,0,arrayOf(segment),false,false,data)
        val text=ReplaceableString("ab!")
        val pos=TransliterationPosition(0,3,0,3)
        assertEquals(UnicodeMatcher.U_MATCH,rule.matchAndReplace(text,pos,false))
        assertEquals("<ab>!",text.toString())
        assertEquals(4,pos.start)
        val partial=TransliterationPosition(0,1,0,1)
        assertEquals(UnicodeMatcher.U_PARTIAL_MATCH,rule.matchAndReplace(ReplaceableString("a"),partial,true))
        val target=ReplaceableString("X")
        assertEquals(0,segment.replace(target,0,1,intArrayOf(77)))
        assertEquals("",target.toString())
    }
}
