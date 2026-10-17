package io.github.heapy.icu4k.text

import com.ibm.icu.text.Normalizer2 as SourceNormalizer
import com.ibm.icu.text.Transform as SourceTransform
import com.ibm.icu.text.Transliterator as SourceTransliterator
import com.ibm.icu.text.UnicodeSet as SourceSet
import com.ibm.icu.lang.UCharacter as SourceCharacter
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class SourceTargetUtilityOracleTest {
    private val type=Class.forName("com.ibm.icu.text.SourceTargetUtility")
    private fun field(target:Any,name:String):Any=type.getDeclaredField(name).apply { isAccessible=true }.get(target)
    @Test fun completeCachesAndFilteredSetsMatchIcu() {
        for(mode in 0..4) {
            var sourceCalls=0;var portCalls=0
            val sn=when(mode) { 1->SourceNormalizer.getNFCInstance();2->SourceNormalizer.getNFKCInstance();3->SourceNormalizer.getNFDInstance();else->null }
            val pn=when(mode) { 1->Normalizer2.getNFCInstance();2->Normalizer2.getNFKCInstance();3->Normalizer2.getNFDInstance();else->null }
            val st=SourceTransform<String,String?> { s -> sourceCalls++;when(mode) { 0->SourceCharacter.toLowerCase(com.ibm.icu.util.ULocale.US,s);1->SourceNormalizer.getNFCInstance().normalize(s);2->SourceNormalizer.getNFKCInstance().normalize(s);4->if(s=="A")null else s;else->s } }
            val pt=Transform<String,String?> { s -> portCalls++;when(mode) { 0->UCharacter.toLowerCase(ULocale.US,s);1->Normalizer2.getNFCInstance().normalize(s);2->Normalizer2.getNFKCInstance().normalize(s);4->if(s=="A")null else s;else->s } }
            val source=type.getDeclaredConstructor(SourceTransform::class.java,SourceNormalizer::class.java).apply {isAccessible=true}.newInstance(st,sn)
            val port=SourceTargetUtility(pt,pn)
            assertEquals(sourceCalls,portCalls,"construction calls $mode")
            val cache=field(source,"sourceCache") as SourceSet
            assertEquals(cache.toPattern(false),port.sourceCache.toPattern(false),"cache $mode")
            assertEquals(cache.isFrozen,port.sourceCache.isFrozen())
            assertEquals(field(source,"sourceStrings"),port.sourceStrings,"strings $mode")
            for(internal in listOf("[A-Za-zÀ-ÿΣΙİß\\u0300-\\u036f]","[a-z]","[]")) for(external in listOf("[A-ZÀ-ÿΣİß\\u0300-\\u036f]","[:any:]")) {
                val owner=SourceTransliterator.getInstance("Null").apply {setFilter(SourceSet(internal))}
                val sf=SourceSet(external);val pf=UnicodeSet(external)
                val ss=SourceSet("[z{zz}]");val ps=UnicodeSet("[z{zz}]")
                val ts=SourceSet("[0{abc}]");val tp=UnicodeSet("[0{abc}]")
                fun result(block:()->Unit):String=try {block();"ok"} catch(e:Throwable){(if(e is InvocationTargetException)e.targetException else e).javaClass.simpleName}
                val sr=result {type.getDeclaredMethod("addSourceTargetSet",SourceTransliterator::class.java,SourceSet::class.java,SourceSet::class.java,SourceSet::class.java).apply {isAccessible=true}.invoke(source,owner,sf,ss,ts)}
                val pr=result {port.addSourceTargetSet({UnicodeSet(it).retainAll(UnicodeSet(internal)).freeze()},pf,ps,tp)}
                assertEquals(sr,pr,"$mode/$internal/$external")
                assertEquals(ss.toPattern(false),ps.toPattern(false));assertEquals(ts.toPattern(false),tp.toPattern(false))
                assertEquals(sourceCalls,portCalls,"set calls $mode")
            }
        }
    }
}
