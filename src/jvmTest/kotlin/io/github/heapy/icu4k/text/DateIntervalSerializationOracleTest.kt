@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DateIntervalInfo as Source
import com.ibm.icu.util.DateInterval as SourceInterval
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.io.*
import java.net.URLClassLoader
import kotlin.test.*

class DateIntervalSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray, from: String? = null, to: String? = null, loader: ClassLoader? = null): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            val name = if (from == null) desc.name else desc.name.replace(from, to!!)
            return Class.forName(name, false, loader ?: javaClass.classLoader)
        }
    }.use { it.readObject() }
    private fun descriptor(a: Class<*>, b: Class<*>) {
        val ac = ObjectStreamClass.lookup(a); val bc = ObjectStreamClass.lookup(b)
        assertEquals(ac.serialVersionUID, bc.serialVersionUID)
        assertEquals(ac.fields.map { Triple(it.name,it.typeCode,it.typeString?.replace("com/ibm/icu/","io/github/heapy/icu4k/")) },bc.fields.map { Triple(it.name,it.typeCode,it.typeString) })
    }
    private fun raw(value: Any): List<Any?> = (value.javaClass.getMethod("getRawPatterns").invoke(value) as Map<*, *>).entries.map { (key, fields) ->
        key to (fields as Map<*, *>).entries.map { (field, info) -> field to listOf(info!!.javaClass.getMethod("getFirstPart").invoke(info),info.javaClass.getMethod("getSecondPart").invoke(info),info.javaClass.getMethod("firstDateInPtnIsLaterDate").invoke(info)) }
    }
    private fun field(value: Any, name: String): Any? = value.javaClass.getDeclaredField(name).also { it.isAccessible=true }.get(value)
    @Test fun exactValueDescriptorsAndBidirectionalEndpoints() {
        descriptor(SourceInterval::class.java,DateInterval::class.java)
        descriptor(Source::class.java,DateIntervalInfo::class.java)
        descriptor(Source.PatternInfo::class.java,DateIntervalInfo.PatternInfo::class.java)
        for ((from,to) in listOf(0L to 0L,10L to -20L,Long.MIN_VALUE to Long.MAX_VALUE,Long.MAX_VALUE to 1L)) {
            val a=SourceInterval(from,to);val b=DateInterval(from,to)
            assertEquals(a.toString(),b.toString());assertEquals(a.hashCode(),b.hashCode())
            val ar=read(bytes(b),"io.github.heapy.icu4k.","com.ibm.icu.") as SourceInterval
            val br=read(bytes(a),"com.ibm.icu.","io.github.heapy.icu4k.") as DateInterval
            assertEquals(a,ar);assertEquals(b,br)
        }
        val a=Source.PatternInfo(null,"d",true);val b=DateIntervalInfo.PatternInfo(null,"d",true)
        assertTrue(b is Cloneable)
        assertEquals(a.toString(),b.toString());assertEquals(a.hashCode(),b.hashCode())
        assertEquals(a,read(bytes(b),"io.github.heapy.icu4k.","com.ibm.icu."))
        assertEquals(b,read(bytes(a),"com.ibm.icu.","io.github.heapy.icu4k."))
    }
    @Test fun bidirectionalLocaleStreamsAndSharedGraphAfterTransientFlagsReset() {
        for (id in listOf("en_US","fr","ar","zh@calendar=chinese","he@calendar=hebrew")) {
            val a=Source(SourceLocale(id));val b=DateIntervalInfo(ULocale(id))
            a.setIntervalPattern(null,1,"latestFirst:y / y");b.setIntervalPattern(null,1,"latestFirst:y / y")
            a.freeze();b.freeze()
            val ar=read(bytes(b),"io.github.heapy.icu4k.","com.ibm.icu.") as Source
            val br=read(bytes(a),"com.ibm.icu.","io.github.heapy.icu4k.") as DateIntervalInfo
            assertEquals(raw(a),raw(br));assertEquals(raw(ar),raw(b))
            assertFalse(br.isFrozen());assertFalse(ar.isFrozen)
        }
        val a=Source(SourceLocale.US);val b=DateIntervalInfo(ULocale.US)
        val ac=a.clone();val bc=b.clone();a.freeze();ac.freeze();b.freeze();bc.freeze()
        val aa=read(bytes(arrayOf(a,ac))) as Array<Source>;val bb=read(bytes(arrayOf(b,bc))) as Array<DateIntervalInfo>
        assertNotSame(bb[0],bb[1]);assertSame(field(bb[0],"fIntervalPatterns"),field(bb[1],"fIntervalPatterns"))
        assertEquals(field(aa[0],"frozen"),field(bb[0],"frozen"));assertEquals(false,field(bb[0],"fIntervalPatternsReadOnly"))
        aa[0].setIntervalPattern("custom",1,"y / y");bb[0].setIntervalPattern("custom",1,"y / y")
        assertNotNull(bb[1].getIntervalPattern("custom",1));assertEquals(raw(aa[1]),raw(bb[1]))
    }
    @Test fun sourceOnlyClassLoaderReadsPortStreamsWithoutPortRuntime() {
        val port=DateIntervalInfo(ULocale.US).apply { setIntervalPattern(null,1,"earliestFirst:y-y");freeze() }
        URLClassLoader(arrayOf(Source::class.java.protectionDomain.codeSource.location),ClassLoader.getPlatformClassLoader()).use { loader ->
            assertFailsWith<ClassNotFoundException> { loader.loadClass(DateIntervalInfo::class.java.name) }
            val isolated=read(bytes(port),"io.github.heapy.icu4k.","com.ibm.icu.",loader)
            assertEquals(raw(port),raw(isolated))
            val restored=read(bytes(isolated),"com.ibm.icu.","io.github.heapy.icu4k.") as DateIntervalInfo
            assertEquals(port,restored);assertEquals(raw(port),raw(restored))
            val interval=read(bytes(DateInterval(-10,20)),"io.github.heapy.icu4k.","com.ibm.icu.",loader)
            assertEquals("-10 20",interval.toString())
        }
    }
}
