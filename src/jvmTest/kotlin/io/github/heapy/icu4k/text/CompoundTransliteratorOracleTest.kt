// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*
import com.ibm.icu.text.Transliterator as Source
import com.ibm.icu.text.ReplaceableString as SourceText
import com.ibm.icu.text.UnicodeSet as SourceSet

class CompoundTransliteratorOracleTest {
    private fun error(action:()->Any?):String=try{val v=action();if(v==null||v==Unit)"OK"else "OK:$v"}catch(e:Throwable){"ERR:"+if(e is IndexOutOfBoundsException)"bounds"else e::class.java.simpleName+":"+e.message}
    @Test fun multiplePassesRulesContextAndSourceTargetSets() {
        val compoundClass=Class.forName("com.ibm.icu.text.CompoundTransliterator")
        val constructor=compoundClass.getDeclaredConstructor(java.util.List::class.java,Int::class.javaPrimitiveType).apply{isAccessible=true}
        val ruleGroups=listOf(emptyList(),listOf("ab > X;"),listOf("ab > X;","X > YY;"),listOf("a > AA;","AA > ;","b > XYZ;"),listOf("x{ab}y > Q;","Q > xyz;"),listOf("[ab]+ > Z;","Z > ab;"),listOf("a > |a;","a > A;"))
        for(rules in ruleGroups)for(anonymous in 0..3)for(filter in listOf(null,"[a-z]","[ab]","[]")) {
            val sourceElements=rules.mapIndexed{i,r->Source.createFromRules(if(i%2==0 || anonymous>1)"%Pass${i+1}"else "Test;Other",r,0)}
            val elements=rules.mapIndexed{i,r->
                val id=if(i%2==0 || anonymous>1)"%Pass${i+1}"else "Test;Other"
                val data=TransliteratorParser(TransliterationFactory{_,_->null}).apply{parse(r,0)}.dataVector.single()
                val state=object:TransliteratorExecutionCore.Input {
                    override val id=id;override var filter:UnicodeSet?=null
                    override fun handleTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean)=data.ruleSet.handleTransliterate(text,pos,incremental)
                }
                val engine=TransliteratorExecutionCore(state)
                object:CompoundTransliteratorCore.Element {
                    override val id=id
                    override val maximumContextLength get()=data.ruleSet.getMaximumContextLength()
                    override fun filteredTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean)=engine.filteredTransliterate(text,pos,incremental)
                    override fun toRules(escapeUnprintable:Boolean)=data.ruleSet.toRules(escapeUnprintable)
                    override fun baseToRules(escapeUnprintable:Boolean)=engine.baseToRules(escapeUnprintable)
                    override fun addSourceTargetSet(filter:UnicodeSet,sourceSet:UnicodeSet,targetSet:UnicodeSet)=data.ruleSet.addSourceTargetSet(filter,sourceSet,targetSet)
                }
            }.toTypedArray<CompoundTransliteratorCore.Element>()
            val source=constructor.newInstance(sourceElements,anonymous) as Source
            var compound:CompoundTransliteratorCore?=null
            val state=object:TransliteratorExecutionCore.Input {
                override val id="";override var filter:UnicodeSet?=null
                override fun handleTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean)=compound!!.handleTransliterate(text,pos,incremental)
            }
            val engine=TransliteratorExecutionCore(state)
            compound=CompoundTransliteratorCore(elements,anonymous,{state.filter},engine::getFilterAsUnicodeSet)
            source.setFilter(filter?.let(::SourceSet));engine.setFilter(filter?.let(::UnicodeSet))
            assertEquals(source.maximumContextLength,compound.computeMaximumContextLength());assertEquals(source.elements.size,compound.getCount())
            for(escape in listOf(false,true))assertEquals(source.toRules(escape),compound.toRules(escape))
            for(external in listOf("[]","[a-z]","[a-zA-Z]")) {
                val ss=SourceSet();val st=SourceSet();val ps=UnicodeSet();val pt=UnicodeSet()
                source.addSourceTargetSet(SourceSet(external),ss,st);compound.addSourceTargetSet(UnicodeSet(external),ps,pt)
                assertEquals(ss.toPattern(false),ps.toPattern(false));assertEquals(st.toPattern(false),pt.toPattern(false))
            }
            for(value in listOf("","ab","aabb","xaby","😀abX","babab")) {
                assertEquals(error{source.transliterate(value)},error{engine.transliterate(value)},"$rules/$anonymous/$filter/$value")
                val st=SourceText();val pt=ReplaceableString();val sp=Source.Position();val pp=TransliterationPosition()
                for(c in value) {
                    assertEquals(error{source.transliterate(st,sp,c.toString())},error{engine.transliterate(pt,pp,c.toString())})
                    assertEquals(st.toString(),pt.toString());assertContentEquals(intArrayOf(sp.start,sp.limit,sp.contextLimit),intArrayOf(pp.start,pp.limit,pp.contextLimit))
                }
                assertEquals(error{source.finishTransliteration(st,sp)},error{engine.finishTransliteration(pt,pp)})
                assertEquals(st.toString(),pt.toString());assertContentEquals(intArrayOf(sp.start,sp.limit,sp.contextLimit),intArrayOf(pp.start,pp.limit,pp.contextLimit))
            }
        }
    }
}
