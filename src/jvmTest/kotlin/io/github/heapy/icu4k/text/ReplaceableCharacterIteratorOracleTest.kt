package io.github.heapy.icu4k.text

import com.ibm.icu.text.Replaceable as SourceReplaceable
import com.ibm.icu.text.ReplaceableString as SourceText
import java.text.CharacterIterator as SourceIterator
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class ReplaceableCharacterIteratorOracleTest {
    private val type=Class.forName("com.ibm.icu.text.BreakTransliterator\$ReplaceableCharacterIterator")
    private fun source(text:SourceText?,begin:Int,end:Int,pos:Int):SourceIterator =
        type.getDeclaredConstructor(SourceReplaceable::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }.newInstance(text,begin,end,pos) as SourceIterator
    private fun result(block:()->Any?):String=try { "ok:"+block() } catch(e:Throwable) {
        val cause=if(e is InvocationTargetException)e.targetException else e
        if(cause is IndexOutOfBoundsException)"bounds" else cause.javaClass.simpleName+":"+cause.message
    }
    @Test fun constructorBoundsAndCursorTransitions() {
        for(input in listOf("","abcd","A😀B")) for(begin in -1..input.length+1) for(end in -1..input.length+1) for(pos in -1..input.length+1) {
            val st=SourceText(input);val pt=ReplaceableString(input)
            assertEquals(result { source(st,begin,end,pos);Unit },result { ReplaceableCharacterIterator(pt,begin,end,pos);Unit })
            if(begin<0 || begin>end || end>input.length || pos<begin || pos>end)continue
            val s=source(st,begin,end,pos);val p=ReplaceableCharacterIterator(pt,begin,end,pos)
            fun compare() { assertEquals(s.beginIndex,p.getBeginIndex());assertEquals(s.endIndex,p.getEndIndex());assertEquals(s.index,p.getIndex());assertEquals(s.current(),p.current()) }
            compare()
            repeat(input.length+2) { assertEquals(s.next(),p.next());compare() }
            repeat(input.length+2) { assertEquals(s.previous(),p.previous());compare() }
            assertEquals(s.first(),p.first());assertEquals(s.last(),p.last())
            for(i in -1..input.length+1) { assertEquals(result { s.setIndex(i) },result { p.setIndex(i) });compare() }
            val sc=s.clone() as SourceIterator;val pc=p.clone()
            assertEquals(s==sc,p==pc)
            st.replace(0,st.length(),"");pt.replace(0,pt.length(),"")
            // clone() is a shallow copy and must not revalidate the now-stale end.
            assertEquals(result { s.clone();Unit },result { p.clone();Unit })
            assertEquals(result { sc.first() },result { pc.first() })
        }
    }
    @Test fun sharedTextCloneAndReset() {
        val st=SourceText("abc");val pt=ReplaceableString("abc")
        val s=source(st,0,3,1);val p=ReplaceableCharacterIterator(pt,0,3,1)
        val sc=s.clone() as SourceIterator;val pc=p.clone()
        st.replace(1,2,"X");pt.replace(1,2,"X")
        assertEquals(sc.current(),pc.current());assertEquals(s==sc,p==pc)
        sc.next();pc.next();assertEquals(s==sc,p==pc)
        type.getDeclaredMethod("setText",SourceReplaceable::class.java).apply {isAccessible=true}.invoke(s,SourceText("Q"))
        p.setText(ReplaceableString("Q"))
        assertEquals(s.first(),p.first());assertEquals(s.endIndex,p.getEndIndex())
        assertEquals(result { source(null,0,0,0) },result { ReplaceableCharacterIterator(null,0,0,0) })
    }
}
