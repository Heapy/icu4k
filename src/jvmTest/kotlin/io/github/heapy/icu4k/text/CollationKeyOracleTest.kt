// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.CollationKey as Source
import com.ibm.icu.text.RawCollationKey as SourceRaw
import com.ibm.icu.text.Collator as SourceCollator
import kotlin.random.Random
import kotlin.test.*

class CollationKeyOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) { when(e) { is IndexOutOfBoundsException -> "bounds";is NullPointerException -> "null";is NegativeArraySizeException -> "negative";else -> e.javaClass.simpleName+":"+e.message } }
    private fun state(s: Source,t: CollationKey) {
        for((a,b) in listOf("m_length_" to "length","m_hashCode_" to "cachedHash")) {
            val expected=Source::class.java.getDeclaredField(a).apply { isAccessible=true }.get(s)
            val actual=CollationKey::class.java.getDeclaredField(b).apply { isAccessible=true }.get(t)
            assertEquals(expected,actual,"$a/$b")
        }
    }
    private fun bytes(value: Source?): Any? = value?.let { listOf(it.sourceString,it.toByteArray().toList()) }
    private fun bytes(value: CollationKey?): Any? = value?.let { listOf(it.getSourceString(),it.toByteArray().toList()) }
    @Test fun realKeysBoundsAndMergingAcrossLocalesAndStrengths() {
        val samples=listOf("","a","A","á","æ","a b","a\ufffeb","日本語","Ω","😀","e\u0301","ch","123")
        for(locale in listOf("root","en","fr","de","zh","ja","es")) for(strength in listOf(0,1,2,3,15)) {
            val c=SourceCollator.getInstance(com.ibm.icu.util.ULocale(locale));c.strength=strength
            val raw=samples.map { c.getCollationKey(it) }
            val arrays=raw.map { it.toByteArray() }
            val sources=raw.mapIndexed { i,k -> Source(k.sourceString,arrays[i].copyOf()) }
            val targets=raw.mapIndexed { i,k -> CollationKey(k.sourceString,arrays[i].copyOf()) }
            for(i in samples.indices) {
                assertEquals(sources[i].hashCode(),targets[i].hashCode());state(sources[i],targets[i])
                for(mode in -2..4) for(levels in -1..7) {
                    assertEquals(outcome { bytes(sources[i].getBound(mode,levels)) },outcome { bytes(targets[i].getBound(mode,levels)) },"bound/$locale/$strength/$i/$mode/$levels")
                    state(sources[i],targets[i])
                }
                for(j in samples.indices) {
                    assertEquals(sources[i].compareTo(sources[j]),targets[i].compareTo(targets[j]))
                    assertEquals(sources[i]==sources[j],targets[i]==targets[j])
                    assertEquals(outcome { bytes(sources[i].merge(sources[j])) },outcome { bytes(targets[i].merge(targets[j])) },"merge/$locale/$strength/$i/$j")
                    state(sources[i],targets[i]);state(sources[j],targets[j])
                }
            }
        }
    }
    @Test fun malformedAliasedArraysAndLazyCaches() {
        val random=Random(78313)
        repeat(1200) { index ->
            val a=ByteArray(random.nextInt(0,30)) { random.nextInt().toByte() };val b=a.copyOf()
            if(a.isNotEmpty() && index%3==0) { val end=random.nextInt(a.size);a[end]=0;b[end]=0 }
            val s=Source("source",a);val t=CollationKey("source",b)
            val other=byteArrayOf(5,1,6,0)
            val s2=Source(null,other);val t2=CollationKey(null,other)
            repeat(8) { step ->
                when(step) {
                    0,5 -> assertEquals(outcome { s.hashCode() },outcome { t.hashCode() })
                    1,6 -> assertEquals(outcome { s.toByteArray().toList() },outcome { t.toByteArray().toList() })
                    2 -> assertEquals(outcome { s.compareTo(s2) },outcome { t.compareTo(t2) })
                    3 -> assertEquals(outcome { s.equals(s2) },outcome { t.equals(t2) })
                    4 -> if(a.isNotEmpty()) { val p=random.nextInt(a.size);val v=random.nextInt().toByte();a[p]=v;b[p]=v }
                    7 -> assertEquals(outcome { bytes(s.merge(s2)) },outcome { bytes(t.merge(t2)) })
                }
                state(s,t)
            }
        }
    }
    @Test fun rawConstructorsReleaseAndComparisonOverload() {
        val random=Random(78314)
        repeat(1000) {
            val bytes=ByteArray(random.nextInt(1,40)) { random.nextInt(1,256).toByte() };bytes[bytes.lastIndex]=0
            val a=SourceRaw(bytes.copyOf(),bytes.size);val b=RawCollationKey(bytes.copyOf(),bytes.size)
            val s=Source("raw",a);val t=CollationKey("raw",b)
            assertNull(a.bytes);assertNull(b.bytes);assertEquals(a.size,b.size);assertEquals(bytes(s),bytes(t));state(s,t)
            val aa=SourceRaw(bytes,bytes.size);val bb=RawCollationKey(bytes,bytes.size)
            val other=byteArrayOf(127,0);val ac=SourceRaw(other,2);val bc=RawCollationKey(other,2)
            assertEquals(aa.compareTo(ac),bb.compareTo(bc))
            assertEquals((aa as com.ibm.icu.util.ByteArrayWrapper).compareTo(ac),(bb as io.github.heapy.icu4k.util.ByteArrayWrapper).compareTo(bc))
        }
    }
}
