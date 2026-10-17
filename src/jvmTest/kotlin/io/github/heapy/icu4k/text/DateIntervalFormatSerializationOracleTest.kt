@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DateIntervalFormat as Source
import com.ibm.icu.text.DateIntervalInfo as SourceInfo
import com.ibm.icu.util.DateInterval as SourceInterval
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.io.*
import java.net.URLClassLoader
import kotlin.test.*

class DateIntervalFormatSerializationOracleTest {
    private val start=1705323723456L
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray,from: String?=null,to: String?=null,loader: ClassLoader?=null):Any=object:ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc:ObjectStreamClass):Class<*> {
            val name=if(from==null) desc.name else desc.name.replace(from,to!!)
            try { return Class.forName(name,false,loader?:javaClass.classLoader) } catch(e:ClassNotFoundException) {
                if(loader!=null) throw e
                return super.resolveClass(desc)
            }
        }
    }.use { it.readObject() }
    private fun field(value:Any,name:String):Any?=value.javaClass.getDeclaredField(name).also { it.isAccessible=true }.get(value)
    private fun descriptor(a:Class<*>,b:Class<*>) {
        val x=ObjectStreamClass.lookup(a);val y=ObjectStreamClass.lookup(b)
        assertEquals(x.serialVersionUID,y.serialVersionUID)
        assertEquals(x.fields.map { Triple(it.name,it.typeCode,it.typeString?.replace("com/ibm/icu/","io/github/heapy/icu4k/")) },y.fields.map { Triple(it.name,it.typeCode,it.typeString) })
    }
    private fun compare(a:Source,b:DateIntervalFormat) {
        for(delta in listOf(0L,1L,1000L,60000L,3600000L,86400000L,34560000000L)) assertEquals(a.format(SourceInterval(start,start+delta)),b.format(DateInterval(start,start+delta)))
        assertEquals(a.timeZone.id,b.getTimeZone().getID())
        assertEquals(a.getContext(com.ibm.icu.text.DisplayContext.Type.CAPITALIZATION).name,b.getContext(DisplayContext.Type.CAPITALIZATION).name)
        assertEquals(a.rawPatterns.toString(),b.getRawPatterns().toString())
    }
    @Test fun exactDescriptorsBidirectionalStreamsAndLegacyCapitalizationDefault() {
        descriptor(Source::class.java,DateIntervalFormat::class.java)
        descriptor(Source.SpanField::class.java,DateIntervalFormat.SpanField::class.java)
        for(id in listOf("en_US","fr","ar","zh@calendar=chinese","he@calendar=hebrew")) for(custom in listOf(false,true)) {
            val a=if(custom) Source.getInstance("yMdHms",SourceLocale(id),SourceInfo(SourceLocale(id))) else Source.getInstance("yMdHms",SourceLocale(id))
            val b=if(custom) DateIntervalFormat.getInstance("yMdHms",ULocale(id),DateIntervalInfo(ULocale(id))) else DateIntervalFormat.getInstance("yMdHms",ULocale(id))
            a.timeZone=com.ibm.icu.util.TimeZone.getTimeZone("Europe/Paris");b.setTimeZone(TimeZone.getTimeZone("Europe/Paris"))
            a.setContext(com.ibm.icu.text.DisplayContext.CAPITALIZATION_FOR_STANDALONE);b.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
            compare(read(bytes(a)) as Source,read(bytes(b)) as DateIntervalFormat)
            compare(read(bytes(b),"io.github.heapy.icu4k.","com.ibm.icu.") as Source,read(bytes(a),"com.ibm.icu.","io.github.heapy.icu4k.") as DateIntervalFormat)
        }
        val a=Source.getInstance("yMMMd",SourceLocale.US);val b=DateIntervalFormat.getInstance("yMMMd",ULocale.US)
        for(owner in listOf(a,b)) owner.javaClass.getDeclaredField("fCapitalizationSetting").also { it.isAccessible=true }.set(owner,null)
        val ar=read(bytes(a)) as Source;val br=read(bytes(b)) as DateIntervalFormat
        assertEquals(DisplayContext.CAPITALIZATION_NONE,br.getContext(DisplayContext.Type.CAPITALIZATION));compare(ar,br)
        assertSame(DateIntervalFormat.SpanField.DATE_INTERVAL_SPAN,read(bytes(DateIntervalFormat.SpanField.DATE_INTERVAL_SPAN)))
        assertSame(Source.SpanField.DATE_INTERVAL_SPAN,read(bytes(DateIntervalFormat.SpanField.DATE_INTERVAL_SPAN),"io.github.heapy.icu4k.","com.ibm.icu."))
        assertSame(DateIntervalFormat.SpanField.DATE_INTERVAL_SPAN,read(bytes(Source.SpanField.DATE_INTERVAL_SPAN),"com.ibm.icu.","io.github.heapy.icu4k."))
    }
    @Test fun isolatedSourceLoaderReadsPortFormatterAndRebuildsTransientPatterns() {
        val b=DateIntervalFormat.getInstance("yMMMdHm",ULocale.US,DateIntervalInfo(ULocale.US)).also { it.setTimeZone(TimeZone.GMT_ZONE) }
        URLClassLoader(arrayOf(Source::class.java.protectionDomain.codeSource.location),ClassLoader.getPlatformClassLoader()).use { loader ->
            assertFailsWith<ClassNotFoundException> { loader.loadClass(DateIntervalFormat::class.java.name) }
            val isolated=read(bytes(b),"io.github.heapy.icu4k.","com.ibm.icu.",loader)
            val sourceInterval=loader.loadClass(SourceInterval::class.java.name).getConstructor(Long::class.javaPrimitiveType,Long::class.javaPrimitiveType).newInstance(start,start+86400000L)
            assertEquals(b.format(DateInterval(start,start+86400000)),isolated.javaClass.getMethod("format",Any::class.java).invoke(isolated,sourceInterval))
            val restored=read(bytes(isolated),"com.ibm.icu.","io.github.heapy.icu4k.") as DateIntervalFormat
            assertEquals(b.format(DateInterval(start,start+86400000)),restored.format(DateInterval(start,start+86400000)))
        }
    }
    @Test fun clonesUseIndependentMonitorsAndKeepFrozenInfoShared() {
        val a=Source.getInstance("yMMMd",SourceLocale.US);val b=DateIntervalFormat.getInstance("yMMMd",ULocale.US)
        val ac=a.clone();val bc=b.clone()
        val graph=read(bytes(arrayOf(b,bc))) as Array<DateIntervalFormat>
        assertSame(field(graph[0],"fInfo"),field(graph[1],"fInfo"))
        assertNotSame(field(graph[0],"fDateFormat"),field(graph[1],"fDateFormat"))
        for((owner,format) in listOf<Pair<Any,()->String>>(a to { ac.format(SourceInterval(start,start+86400000)) },b to { bc.format(DateInterval(start,start+86400000)) })) {
            val done=java.util.concurrent.CountDownLatch(1);val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
            lateinit var worker:Thread
            val independent=synchronized(owner) {
                worker=Thread { try { format() }catch(e:Throwable){failure.set(e)}finally{done.countDown()} }.apply { isDaemon=true;start() }
                done.await(5,java.util.concurrent.TimeUnit.SECONDS)
            }
            worker.join(5000);failure.get()?.let { throw it };assertTrue(independent,owner.javaClass.name)
        }
    }
}
