package io.github.heapy.icu4k.text

import com.ibm.icu.impl.UCaseProps as SourceCase
import com.ibm.icu.text.ReplaceableString as SourceText
import com.ibm.icu.text.Replaceable as SourceReplaceable
import io.github.heapy.icu4k.impl.UCaseProps
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class ReplaceableContextIteratorOracleTest {
    private val type=Class.forName("com.ibm.icu.text.ReplaceableContextIterator")
    private fun source(): Any=type.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
    private fun invoke(target:Any,name:String,vararg args:Any?):Any? {
        val types=args.map { when(it) { is Int -> Int::class.javaPrimitiveType!!; is String -> String::class.java; else -> SourceReplaceable::class.java } }.toTypedArray()
        try { return type.getDeclaredMethod(name,*types).apply { isAccessible=true }.invoke(target,*args) }
        catch(e:InvocationTargetException) { throw e.targetException }
    }
    private fun result(block:()->Any?):String=try { "ok:"+block() } catch(e:Throwable) { if(e is IndexOutOfBoundsException)"bounds" else e.javaClass.simpleName }
    @Test fun contextLimitsDirectionsReplacementAndInvalidIndexes() {
        val inputs=listOf("","abc","A😀Σ",charArrayOf('a','\ud800','b','\udc00').concatToString())
        for(input in inputs) for(start in -1..input.length+1) for(limit in -1..input.length+1) for(direction in -2..2) {
            val java=source(); val port=ReplaceableContextIterator()
            val st=SourceText(input); val pt=ReplaceableString(input)
            invoke(java,"setText",st); port.setText(pt)
            invoke(java,"setIndex",start); port.setIndex(start)
            invoke(java,"setLimit",limit); port.setLimit(limit)
            invoke(java,"setContextLimits",start-1,limit+1); port.setContextLimits(start-1,limit+1)
            repeat(3) {
                assertEquals(result { invoke(java,"nextCaseMapCP") },result { port.nextCaseMapCP() },"$start/$limit/$direction")
                assertEquals(invoke(java,"getCaseMapCPStart"),port.getCaseMapCPStart())
                invoke(java,"reset",direction); port.reset(direction)
                repeat(input.length+2) {
                    assertEquals(result { invoke(java,"next") },result { port.next() })
                    assertEquals(invoke(java,"didReachLimit"),port.didReachLimit())
                }
                val replacement=if(it%2==0)"XY" else ""
                assertEquals(result { invoke(java,"replace",replacement) },result { port.replace(replacement) })
                assertEquals(st.toString(),pt.toString())
            }
        }
    }
    @Test fun fullMappingsUseUpdatedMutableContext() {
        val texts=listOf("Iİıi ΣΟΣ ΟΣ́A", "I\u0301 J\u0307 I\u0307", "ß ﬃ ǰ ǅ 😀Σ", charArrayOf('\ud800','I','\udc00','Σ').concatToString())
        for(input in texts) for(language in listOf("en","tr","az","lt","el")) for(mode in 0..3) {
            val java=source(); val port=ReplaceableContextIterator()
            val st=SourceText(input); val pt=ReplaceableString(input)
            invoke(java,"setText",st); port.setText(pt)
            val sc=SourceCase.getCaseLocale(com.ibm.icu.util.ULocale(language))
            val pc=UCaseProps.getCaseLocale(language)
            while(true) {
                val c=invoke(java,"nextCaseMapCP") as Int
                assertEquals(c,port.nextCaseMapCP())
                if(c<0)break
                val so=StringBuilder(); val po=StringBuilder()
                val si=java as SourceCase.ContextIterator
                val sr=when(mode) { 0->SourceCase.INSTANCE.toFullLower(c,si,so,sc); 1->SourceCase.INSTANCE.toFullUpper(c,si,so,sc); 2->SourceCase.INSTANCE.toFullTitle(c,si,so,sc); else->SourceCase.INSTANCE.toFullFolding(c,so,0) }
                val pr=when(mode) { 0->UCaseProps.INSTANCE.toFullLower(c,port,po,pc); 1->UCaseProps.INSTANCE.toFullUpper(c,port,po,pc); 2->UCaseProps.INSTANCE.toFullTitle(c,port,po,pc); else->UCaseProps.INSTANCE.toFullFolding(c,po,0) }
                assertEquals(sr,pr); assertEquals(so.toString(),po.toString()); assertEquals(invoke(java,"didReachLimit"),port.didReachLimit())
                if(sr>=0) {
                    val replacement=if(sr<=31)so.toString() else UTF16.valueOf(sr)
                    assertEquals(invoke(java,"replace",replacement),port.replace(replacement))
                }
                assertEquals(st.toString(),pt.toString())
            }
        }
    }
}
