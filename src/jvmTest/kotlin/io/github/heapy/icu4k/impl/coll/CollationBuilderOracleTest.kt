// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.CollationBuilder as Source
import com.ibm.icu.impl.coll.CollationTailoring as SourceTailoring
import kotlin.test.*

class CollationBuilderOracleTest {
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun error(t: Throwable?): Any? {
        if(t==null) return null
        return listOf(t.javaClass.simpleName,if(t is NullPointerException || t is IndexOutOfBoundsException) null else t.message,if(t is java.text.ParseException) t.errorOffset else null,error(t.cause))
    }
    private fun outcome(block: () -> Unit): Any? = try { block();null } catch(e: Throwable) { error(e) }
    private fun compare(rule: String): Pair<SourceTailoring?,CollationTailoring?> {
        val a=Source(com.ibm.icu.impl.coll.CollationRoot.getRoot());val b=CollationBuilder(CollationRoot.getRoot())
        var sa: SourceTailoring?=null;var tb: CollationTailoring?=null
        assertEquals(outcome { sa=a.parseAndBuild(rule) },outcome { tb=b.parseAndBuild(rule) },rule)
        assertEquals(field(a,"cesLength"),field(b,"cesLength"));assertContentEquals(field(a,"ces") as LongArray,field(b,"ces") as LongArray)
        assertEquals((field(a,"optimizeSet") as com.ibm.icu.text.UnicodeSet).toPattern(false),(field(b,"optimizeSet") as io.github.heapy.icu4k.text.UnicodeSet).toPattern(false))
        val graph=field(b,"graph") as CollationNodeGraph
        for(name in listOf("nodes","rootPrimaryIndexes")) {
            val source=field(a,name)!!;val target=if(name=="nodes") graph.nodes else graph.rootPrimaryIndexes
            val sourceBuffer=source.javaClass.getMethod("getBuffer").invoke(source);val targetBuffer=target.javaClass.getMethod("getBuffer").invoke(target)
            if(sourceBuffer is LongArray) assertContentEquals(sourceBuffer,targetBuffer as LongArray,rule) else assertContentEquals(sourceBuffer as IntArray,targetBuffer as IntArray,rule)
        }
        if(sa!=null) {
            val s=sa!!;val t=tb!!;assertEquals(s.version,t.version);assertEquals(s.rules,t.getRules());assertEquals(s.actualLocale.name,t.actualLocale!!.getName())
            assertEquals(s.data===com.ibm.icu.impl.coll.CollationRoot.getData(),t.data===CollationRoot.getData())
            val ss=s.settings.readOnly();val ts=t.settings!!.readOnly()!!;assertEquals(ss.options,ts.options);assertEquals(ss.variableTop,ts.variableTop);assertEquals(ss.fastLatinOptions,ts.fastLatinOptions);assertContentEquals(ss.fastLatinPrimaries,ts.fastLatinPrimaries)
            val d=CollationDataOracleTest().importSource(s.data);val td=t.data!!
            assertContentEquals(d.trie!!.serialize(),td.trie!!.serialize(),"trie/$rule");assertContentEquals(d.ce32s,td.ce32s,"CE32s/$rule");assertContentEquals(d.ces,td.ces,"CEs/$rule");assertEquals(d.contexts,td.contexts,"contexts/$rule")
            assertContentEquals(d.jamoCE32s,td.jamoCE32s);assertEquals(d.unsafeBackwardSet,td.unsafeBackwardSet);assertContentEquals(d.fastLatinTable,td.fastLatinTable);assertContentEquals(d.fastLatinTableHeader,td.fastLatinTableHeader)
        }
        return sa to tb
    }
    @Test fun completeCompilationCanonicalClosureAndErrors() {
        for(rule in listOf("","[strength 1]","&a<b<c<<d<<<e<<<<f","&[before 1]b<a","&[before 2]a<<à<<á","&[before 3]A<<<B<<<C","&a<é<è","&a<x|b<xy|b<x|bc / d","&a<😀|b<😀|bc/e","&a<ch<cH<Ch<CH","&a=b쫊쫊","[import de-u-co-phonebk]","[optimize [a-z]]&a<ä/e","[suppressContractions [a-z]]&a<b","&a<ᄀ","&a<가","&a<<<<b<<<<c<<<<d<<<<e","&[last implicit]<a","&[last trailing]<a")) compare(rule)
        for(position in listOf("first tertiary ignorable","last tertiary ignorable","first secondary ignorable","last secondary ignorable","first primary ignorable","last primary ignorable","first variable","last variable","first regular","last regular","first implicit","first trailing")) for(strength in listOf("<","<<","<<<")) compare("&[$position] $strength a")
    }
}
