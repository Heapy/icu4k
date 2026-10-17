@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.MeasureFormat as Source
import com.ibm.icu.text.TimeUnitFormat as SourceTime
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.io.*
import java.net.URLClassLoader
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class MeasureFormatSerializationOracleTest {
    private fun bytes(value:Any):ByteArray=ByteArrayOutputStream().also { out->ObjectOutputStream(out).use { it.writeObject(value) } }.toByteArray()
    private fun read(data:ByteArray,from:String?=null,to:String?=null,loader:ClassLoader?=null):Any=object:ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc:ObjectStreamClass):Class<*> {
            val name=if(from==null) desc.name else desc.name.replace(from,to!!)
            try { return Class.forName(name,false,loader?:javaClass.classLoader) }catch(e:ClassNotFoundException){if(loader!=null) throw e;return super.resolveClass(desc)}
        }
    }.use { it.readObject() }
    private fun descriptor(a:Class<*>,b:Class<*>) {
        val x=ObjectStreamClass.lookup(a);val y=ObjectStreamClass.lookup(b)
        assertEquals(x.serialVersionUID,y.serialVersionUID)
        assertEquals(x.fields.map { Triple(it.name,it.typeCode,it.typeString?.replace("com/ibm/icu/","io/github/heapy/icu4k/")) },y.fields.map { Triple(it.name,it.typeCode,it.typeString) })
    }
    private fun compare(a:Source,b:MeasureFormat) {
        assertEquals(a.locale.toString(),b.getLocale().toString());assertEquals(a.width.name,b.getWidth()!!.name)
        val x=com.ibm.icu.util.CurrencyAmount(12.5,com.ibm.icu.util.Currency.getInstance("USD"));val y=CurrencyAmount(12.5,Currency.getInstance("USD"))
        assertEquals(a.format(x),b.format(y))
        if(a.javaClass.simpleName!="CurrencyFormat") {
            assertEquals(a.formatMeasures(com.ibm.icu.util.Measure(5,com.ibm.icu.util.MeasureUnit.HOUR),com.ibm.icu.util.Measure(7.5,com.ibm.icu.util.MeasureUnit.SECOND)),b.formatMeasures(Measure(5,MeasureUnit.HOUR),Measure(7.5,MeasureUnit.SECOND)))
        }
        if(a is SourceTime && b is TimeUnitFormat) {
            val ap=java.text.ParsePosition(0);val bp=ParsePosition(0)
            assertEquals(outcome { a.parseObject("2 min",ap)?.toString() },outcome { b.parseObject("2 min",bp)?.toString() });assertEquals(ap.index,bp.index)
        }
    }
    @Test fun exactProxyAndLegacyDescriptorsBidirectionalAndSourceOnlyStreams() {
        descriptor(Source::class.java,MeasureFormat::class.java)
        descriptor(SourceTime::class.java,TimeUnitFormat::class.java)
        descriptor(Source.getCurrencyFormat().javaClass,CurrencyFormat::class.java)
        descriptor(Class.forName("com.ibm.icu.text.MeasureFormat\$MeasureProxy"),MeasureFormat.MeasureProxy::class.java)
        for(id in listOf("en_US","fr","ar","en@numbers=thai")) for(width in MeasureFormat.FormatWidth.entries) {
            val a=Source.getInstance(SourceLocale(id),Source.FormatWidth.valueOf(width.name));val b=MeasureFormat.getInstance(ULocale(id),width)
            compare(read(bytes(a)) as Source,read(bytes(b)) as MeasureFormat)
            compare(read(bytes(b),"io.github.heapy.icu4k.","com.ibm.icu.") as Source,read(bytes(a),"com.ibm.icu.","io.github.heapy.icu4k.") as MeasureFormat)
        }
        for(id in listOf("en_US","fr","ja")) {
            for(style in 0..1) {
                val a=SourceTime(SourceLocale(id),style);val b=TimeUnitFormat(ULocale(id),style)
                a.setNumberFormat(com.ibm.icu.text.DecimalFormat("0.000"));b.setNumberFormat(DecimalFormat("0.000"))
                a.setLocale(SourceLocale.JAPAN);b.setLocale(ULocale.JAPAN)
                compare(read(bytes(b),"io.github.heapy.icu4k.","com.ibm.icu.") as Source,read(bytes(a),"com.ibm.icu.","io.github.heapy.icu4k.") as MeasureFormat)
            }
            compare(read(bytes(Source.getCurrencyFormat(SourceLocale(id)))) as Source,read(bytes(MeasureFormat.getCurrencyFormat(ULocale(id)))) as MeasureFormat)
        }
        URLClassLoader(arrayOf(Source::class.java.protectionDomain.codeSource.location),ClassLoader.getPlatformClassLoader()).use { loader->
            assertFailsWith<ClassNotFoundException> { loader.loadClass(MeasureFormat::class.java.name) }
            for(b in listOf(MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.NUMERIC),TimeUnitFormat(ULocale.US),MeasureFormat.getCurrencyFormat(ULocale.US))) {
                val isolated=read(bytes(b),"io.github.heapy.icu4k.","com.ibm.icu.",loader)
                val restored=read(bytes(isolated),"com.ibm.icu.","io.github.heapy.icu4k.") as MeasureFormat
                assertEquals(b.getLocale(),restored.getLocale());assertEquals(b.getWidth(),restored.getWidth())
            }
        }
    }
    private fun external(width:Int,kind:Int,nf:Any?,map:Any?,version:Int=0):ByteArray=ByteArrayOutputStream().also { out->ObjectOutputStream(out).use {
        it.writeByte(version);it.writeUTF("en-US");it.writeByte(width);it.writeObject(nf);it.writeByte(kind);it.writeObject(map)
    } }.toByteArray()
    private fun outcome(action:()->Any?):String=try { "ok:${action()}" }catch(e:Exception){val x=if(e is InvocationTargetException) e.targetException else e;when(x){is ClassCastException->"cast";is NullPointerException->"null";else->x.javaClass.simpleName+":"+x.message}}
    @Test fun malformedProxyPayloadsAndUnknownWidthsRetainSourceValidationOrder() {
        val clazz=Class.forName("com.ibm.icu.text.MeasureFormat\$MeasureProxy")
        val ctor=clazz.getConstructor().also { it.isAccessible=true }
        val sourceRead=clazz.getMethod("readExternal",ObjectInput::class.java).also { it.isAccessible=true }
        val sourceResolve=clazz.getDeclaredMethod("readResolve").also { it.isAccessible=true }
        val portResolve=MeasureFormat.MeasureProxy::class.java.getDeclaredMethod("readResolve").also { it.isAccessible=true }
        val an=com.ibm.icu.text.DecimalFormat("0.00");val bn=DecimalFormat("0.00")
        for(width in listOf(0,1,2,3,4,5,255)) for(kind in listOf(0,1,2,255)) for(mode in 0..3) {
            val a=ctor.newInstance();val b=MeasureFormat.MeasureProxy()
            val am=when(mode){0->HashMap<Any,Any>();1->null;2->java.util.TreeMap<Any,Any>();else->HashMap<Any,Any>()}
            val nfA=if(mode==3) null else an;val nfB=if(mode==3) null else bn
            val ao=outcome { ObjectInputStream(ByteArrayInputStream(external(width,kind,nfA,am,255))).use { sourceRead.invoke(a,it) };val result=sourceResolve.invoke(a) as Source;result.width.name+"/"+result.javaClass.simpleName }
            val bo=outcome { ObjectInputStream(ByteArrayInputStream(external(width,kind,nfB,am,255))).use { b.readExternal(it) };val result=portResolve.invoke(b) as MeasureFormat;result.getWidth()!!.name+"/"+result.javaClass.simpleName }
            assertEquals(ao,bo,"$width/$kind/$mode")
        }
    }
    @Test fun clonedFormatterCacheUsesTheCloneMonitor() {
        val a=Source.getInstance(SourceLocale.US,Source.FormatWidth.WIDE);val b=MeasureFormat.getInstance(ULocale.US,MeasureFormat.FormatWidth.WIDE)
        val ac=a.clone() as Source;val bc=b.clone() as MeasureFormat
        for((owner,format) in listOf<Pair<Any,()->String>>(a to { ac.format(com.ibm.icu.util.Measure(1,com.ibm.icu.util.MeasureUnit.FOOT)) },b to { bc.format(Measure(1,MeasureUnit.FOOT)) })) {
            val done=java.util.concurrent.CountDownLatch(1);val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>();lateinit var worker:Thread
            val independent=synchronized(owner) {
                worker=Thread { try{format()}catch(e:Throwable){failure.set(e)}finally{done.countDown()} }.apply { isDaemon=true;start() }
                done.await(5,java.util.concurrent.TimeUnit.SECONDS)
            }
            worker.join(5000);failure.get()?.let { throw it };assertTrue(independent,owner.javaClass.name)
        }
    }
}
