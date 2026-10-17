// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class CompoundTransliteratorCoreTest {
    private val groups=listOf(emptyList(),listOf("ab > X;","X > YY;"),listOf("a > AA;","AA > ;","b > XYZ;"),listOf("x{ab}y > Q;","Q > xyz;"),listOf("[ab]+ > Z;","Z > ab;"))
    // Independent ICU4J 78.3 CompoundTransliterator/public API fingerprints.
    private val expected=intArrayOf(-489598113,213891613,396603651,-262916355,-1477556403)
    @Test fun emptyCompound()=verify(0)
    @Test fun expandingPasses()=verify(1)
    @Test fun deletingPasses()=verify(2)
    @Test fun contextPasses()=verify(3)
    @Test fun quantifiedPasses()=verify(4)
    private fun verify(k:Int) {
        var hash=0x811c9dc5.toInt()
        fun add(v:Int){hash=(hash xor v)*16777619}
        fun string(s:String){add(s.length);for(c in s)add(c.code)}
        fun error(e:Throwable?){if(e==null)add(0)else{add(1);string("RuntimeException:"+e.message)}}
        fun snap(t:ReplaceableString,p:TransliterationPosition){string(t.toString());add(p.contextStart);add(p.contextLimit);add(p.start);add(p.limit)}
        for(anonymous in listOf(0,2))for(filter in listOf(null,"[a-z]","[ab]","[]")) {
            val elements=groups[k].mapIndexed{i,r->
                val fixture=TransliterationExecutionFixture(r,"%Pass${i+1}")
                object:CompoundTransliteratorCore.Element {
                    override val id=fixture.state.id
                    override val maximumContextLength get()=fixture.data.ruleSet.getMaximumContextLength()
                    override fun filteredTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean)=fixture.engine.filteredTransliterate(text,pos,incremental)
                    override fun toRules(escapeUnprintable:Boolean)=fixture.data.ruleSet.toRules(escapeUnprintable)
                    override fun baseToRules(escapeUnprintable:Boolean)=fixture.engine.baseToRules(escapeUnprintable)
                    override fun addSourceTargetSet(filter:UnicodeSet,sourceSet:UnicodeSet,targetSet:UnicodeSet)=fixture.data.ruleSet.addSourceTargetSet(filter,sourceSet,targetSet)
                }
            }.toTypedArray<CompoundTransliteratorCore.Element>()
            var compound:CompoundTransliteratorCore?=null
            val state=object:TransliteratorExecutionCore.Input {
                override val id="";override var filter:UnicodeSet?=null
                override fun handleTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean)=compound!!.handleTransliterate(text,pos,incremental)
            }
            val engine=TransliteratorExecutionCore(state)
            compound=CompoundTransliteratorCore(elements,anonymous,{state.filter},engine::getFilterAsUnicodeSet)
            engine.setFilter(filter?.let(::UnicodeSet))
            add(compound.computeMaximumContextLength())
            for(escape in listOf(false,true))string(compound.toRules(escape))
            for(external in listOf("[]","[a-z]","[a-zA-Z]")) {
                val source=UnicodeSet();val target=UnicodeSet();compound.addSourceTargetSet(UnicodeSet(external),source,target)
                string(source.toPattern(false));string(target.toPattern(false))
            }
            for(value in listOf("","ab","aabb","xaby","😀abX","babab")) {
                try{string(engine.transliterate(value));error(null)}catch(e:Throwable){error(e)}
                val text=ReplaceableString();val pos=TransliterationPosition()
                for(c in value){try{engine.transliterate(text,pos,c.toString());error(null)}catch(e:Throwable){error(e)};snap(text,pos)}
                try{engine.finishTransliteration(text,pos);error(null)}catch(e:Throwable){error(e)};snap(text,pos)
            }
        }
        assertEquals(expected[k],hash)
    }
}
