package io.github.heapy.icu4k.text

import com.ibm.icu.text.Transliterator as Source
import com.ibm.icu.text.UnicodeSet as SourceSet
import com.ibm.icu.text.ReplaceableString as SourceText
import kotlin.test.*

class TransliteratorPublicOracleTest {
    private fun error(block:()->Any?):String=try {block();"ok"}catch(e:Throwable){(if(e is IndexOutOfBoundsException)"bounds" else e.javaClass.simpleName)+":"+(if(e is IndexOutOfBoundsException)"" else e.message)}
    private fun compare(source:Source,port:Transliterator,input:String) {
        assertEquals(source.getID(),port.getID())
        assertEquals(source.getMaximumContextLength(),port.getMaximumContextLength())
        for(escape in listOf(false,true))assertEquals(source.toRules(escape),port.toRules(escape),source.getID())
        assertEquals(source.getElements().map{it.getID()},port.getElements().map{it.getID()},source.getID())
        val st=SourceText(input);val pt=ReplaceableString(input)
        assertEquals(error{source.transliterate(st,0,st.length())},error{port.transliterate(pt,0,pt.length())},source.getID())
        assertEquals(st.toString(),pt.toString(),"${source.getID()}/$input")
        val si=Source.Position();val pi=Transliterator.Position()
        val ss=SourceText();val ps=ReplaceableString()
        for(c in input) {
            assertEquals(error{source.transliterate(ss,si,c.toString())},error{port.transliterate(ps,pi,c.toString())},source.getID())
            assertEquals(ss.toString(),ps.toString(),source.getID())
            assertEquals(si.toString(),pi.toString(),source.getID())
        }
        assertEquals(error{source.finishTransliteration(ss,si)},error{port.finishTransliteration(ps,pi)},source.getID())
        assertEquals(ss.toString(),ps.toString(),source.getID());assertEquals(si.toString(),pi.toString(),source.getID())
    }
    @Test fun everyBuiltinBatchStreamingFiltersAndInverse() {
        val ids=listOf("Null","Remove","Lower","Upper","Title","CaseFold","NFC","NFD","NFKC","NFKD","FCD","FCC","Name","Name-Any",
            "Any-Hex","Any-Hex/Unicode","Any-Hex/Java","Any-Hex/C","Any-Hex/XML","Any-Hex/XML10","Any-Hex/Perl","Any-Hex/Plain",
            "Hex-Any","Hex-Any/Unicode","Hex-Any/Java","Hex-Any/C","Hex-Any/XML","Hex-Any/XML10","Hex-Any/Perl","Any-BreakInternal")
        val inputs=listOf("", "aBß ﬃ ǅ IİΣΟΣ", "a\u0301\u0327 Å Ａ ﾊﾟ 😀", "\\u0041 \\U0001F600 U+0042 &#x43; &#68; \\x{45}", "\\N{ LATIN CAPITAL LETTER A } \\N{<control-0000>}", "ภาษาไทยภาษาไทย", charArrayOf('a','\ud800','b','\udc00').concatToString())
        for(id in ids) for(direction in 0..1) {
            var source:Source?=null;var port:Transliterator?=null
            assertEquals(error{source=Source.getInstance(id,direction)},error{port=Transliterator.getInstance(id,direction)},"$id/$direction")
            if(source==null || port==null)continue
            for(filter in listOf(null,"[a-zA-ZΣΙİß\\u0300-\\u036f]","[]")) {
                source!!.setFilter(filter?.let(::SourceSet));port!!.setFilter(filter?.let(::UnicodeSet))
                for(input in inputs)compare(source!!,port!!,input)
            }
        }
    }
    @Test fun rulesFunctionsAliasesAndCompoundPasses() {
        val rules=listOf("", "a > b;", "::[a]; a > b;", "::Upper;", "a>b; ::Null; b>c;", "::NFD; [[:Mn:]] > ; ::NFC;", "(ab) > $"+"1 $"+"1;", "a > &Any-Upper(ß);", "::[ab]; a>b; ::Upper;", "use maximum backup 1;")
        for(rule in rules)for(dir in 0..1) {
            var source:Source?=null;var port:Transliterator?=null
            assertEquals(error{source=Source.createFromRules("custom",rule,dir)},error{port=Transliterator.createFromRules("custom",rule,dir)},"$rule/$dir")
            if(source!=null && port!=null)for(input in listOf("", "aabb ß ΣΟΣ á a\u0301", "😀ab"))compare(source!!,port!!,input)
        }
    }
    @Test fun resourceRulesAndAnyScriptDispatch() {
        val ids=listOf("Latin-Greek","Greek-Latin","Cyrillic-Latin","Latin-Cyrillic","Latin-ASCII","Any-Latin","Any-Greek","Han-Latin","Devanagari-Latin","Latin-Devanagari","el-Latin")
        for(id in ids)for(dir in 0..1) {
            var source:Source?=null;var port:Transliterator?=null
            assertEquals(error{source=Source.getInstance(id,dir)},error{port=Transliterator.getInstance(id,dir)},"$id/$dir")
            if(source!=null && port!=null)for(input in listOf("abc Straße ΣΟΣ ΑΒΓ Москва 中文 नमस्ते ＡＢＣ ﾊﾟ", "a\u0301 😀Σ abc"))compare(source!!,port!!,input)
        }
    }
    @Test fun registryInventoryDisplayNamesAndVariants() {
        fun sourceValues(e:java.util.Enumeration<String>)=buildList {while(e.hasMoreElements())add(e.nextElement())}
        fun values(e:io.github.heapy.icu4k.util.Enumeration<String>)=buildList {while(e.hasMoreElements())add(e.nextElement())}
        val ids=sourceValues(Source.getAvailableIDs())
        assertEquals(ids,values(Transliterator.getAvailableIDs()))
        assertEquals(sourceValues(Source.getAvailableSources()).toSet(),values(Transliterator.getAvailableSources()).toSet())
        for(id in ids)for(locale in listOf("en","el","ja","zz"))assertEquals(Source.getDisplayName(id,com.ibm.icu.util.ULocale(locale)),Transliterator.getDisplayName(id,io.github.heapy.icu4k.util.ULocale(locale)),"$id/$locale")
        for(source in sourceValues(Source.getAvailableSources())) {
            val targets=sourceValues(Source.getAvailableTargets(source))
            assertEquals(targets.toSet(),values(Transliterator.getAvailableTargets(source)).toSet())
            for(target in targets)assertEquals(sourceValues(Source.getAvailableVariants(source,target)),values(Transliterator.getAvailableVariants(source,target)),"$source/$target")
        }
    }
    @Test fun everyRegisteredTransformMatchesOnSmallCorpus() {
        val ids=Source.getAvailableIDs().toList()
        for(id in ids) {
            var source:Source?=null;var port:Transliterator?=null
            assertEquals(error{source=Source.getInstance(id)},error{port=Transliterator.getInstance(id)},id)
            if(source!=null && port!=null) {
                assertEquals(source!!.toRules(false),port!!.toRules(false),id)
                val input="aAß á Σ Москва 中文 क Ａ 😀"
                val st=SourceText(input);val pt=ReplaceableString(input)
                assertEquals(error{source!!.transliterate(st)},error{port!!.transliterate(pt)},id)
                assertEquals(st.toString(),pt.toString(),id)
            }
        }
    }
    @Test fun customFactoriesAliasesSharedInstancesAndPositionIdentity() {
        val id="HeapyFixture-Transform"
        val alias="HeapyFixture-Alias"
        val sourceCalls=mutableListOf<String>();val portCalls=mutableListOf<String>()
        try {
            Source.registerFactory(id){requested ->sourceCalls.add(requested);object:Source("factory",null){override fun handleTransliterate(text:com.ibm.icu.text.Replaceable,pos:Source.Position,incremental:Boolean){pos.start=pos.limit}}}
            Transliterator.registerFactory(id){requested ->portCalls.add(requested);object:Transliterator("factory",null){override fun handleTransliterate(text:Replaceable,pos:Position,incremental:Boolean){pos.start=pos.limit}}}
            Source.registerAlias(alias,id);Transliterator.registerAlias(alias,id)
            for(query in listOf(id,alias,id.lowercase())) {
                val source=Source.getInstance(query);val port=Transliterator.getInstance(query)
                compare(source,port,"abc")
                assertEquals(sourceCalls,portCalls)
            }
            var seen:Transliterator.Position?=null
            val shared=object:Transliterator(id,null){override fun handleTransliterate(text:Replaceable,pos:Position,incremental:Boolean){seen=pos;pos.start=pos.limit}}
            Transliterator.registerInstance(shared)
            assertSame(shared,Transliterator.getInstance(id.lowercase()))
            assertEquals(id.lowercase(),shared.getID())
            val pos=Transliterator.Position(0,3,0,3)
            shared.filteredTransliterate(ReplaceableString("abc"),pos,false)
            assertSame(pos,seen)
            val source=Source.createFromRules(id,"a>b;",0);val port=Transliterator.createFromRules(id,"a>b;",0)
            Source.registerInstance(source);Transliterator.registerInstance(port)
            val sc=Source.getInstance(id);val pc=Transliterator.getInstance(id)
            assertNotSame(source,sc);assertNotSame(port,pc)
            assertEquals((sc as com.ibm.icu.text.RuleBasedTransliterator).safeClone().toRules(false),(pc as RuleBasedTransliterator).safeClone().toRules(false))
        } finally {Source.unregister(id);Source.unregister(alias);Transliterator.unregister(id);Transliterator.unregister(alias)}
    }

    class DeclaredSource : Source("declared",null) {override fun handleTransliterate(text:com.ibm.icu.text.Replaceable,pos:Source.Position,incremental:Boolean){pos.start=pos.limit}}
    class DeclaredPort : Transliterator("declared",null) {override fun handleTransliterate(text:Replaceable,pos:Position,incremental:Boolean){pos.start=pos.limit}}
    @Test fun classRegistrationAndDisplayNameCache() {
        val id="HeapyFixture-Reflect"
        try {
            Source.registerClass(id,DeclaredSource::class.java,"Fixture display")
            Transliterator.registerClass(id,DeclaredPort::class.java,"Fixture display")
            assertEquals(Source.getDisplayName(id),Transliterator.getDisplayName(id))
            compare(Source.getInstance(id),Transliterator.getInstance(id),"abc")
            assertNotSame(Transliterator.getInstance(id),Transliterator.getInstance(id))
        }finally {Source.unregister(id);Transliterator.unregister(id)}
        assertEquals(Source.getDisplayName(id),Transliterator.getDisplayName(id))
    }
    @Test fun publicSourceTargetSetsAndFilterCopies() {
        for(id in listOf("Null","Remove","Lower","Upper","Title","CaseFold","NFC","NFD","NFKC","NFKD","Any-Hex/C","Hex-Any","Name","Name-Any","Any-Latin","Any-BreakInternal")) {
            val source=Source.getInstance(id);val port=Transliterator.getInstance(id)
            for(pattern in listOf("[A-Za-zΣΙİßá\\u0300-\\u036f]","[]")) {
                source.setFilter(SourceSet(pattern));val copy=UnicodeSet(pattern);port.setFilter(copy);copy.clear()
                assertEquals((source.getFilter() as SourceSet).toPattern(false),(port.getFilter() as UnicodeSet).toPattern(false))
                assertTrue((port.getFilter() as UnicodeSet).isFrozen())
                for(input in listOf("[A-Záß\\u0300-\\u036f]","[:any:]")) {
                    val ss=SourceSet("[z]");val ps=UnicodeSet("[z]");val st=SourceSet("[0]");val pt=UnicodeSet("[0]")
                    source.addSourceTargetSet(SourceSet(input),ss,st);port.addSourceTargetSet(UnicodeSet(input),ps,pt)
                    assertEquals(ss.toPattern(false),ps.toPattern(false),id);assertEquals(st.toPattern(false),pt.toPattern(false),id)
                }
                assertEquals(source.getSourceSet().toPattern(false),port.getSourceSet().toPattern(false),id)
                assertEquals(source.getTargetSet().toPattern(false),port.getTargetSet().toPattern(false),id)
            }
        }
    }

}
