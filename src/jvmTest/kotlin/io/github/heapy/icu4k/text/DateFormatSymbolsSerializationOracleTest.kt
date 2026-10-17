@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DateFormatSymbols as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import kotlin.test.*

class DateFormatSymbolsSerializationOracleTest {
    private fun bytes(value:Any):ByteArray = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
    private fun read(bytes:ByteArray,from:String?=null,to:String?=null):Any = object:ObjectInputStream(ByteArrayInputStream(bytes)) {
        override fun resolveClass(desc:ObjectStreamClass):Class<*> {
            if(from!=null && desc.name.contains(from)) {
                try { return Class.forName(desc.name.replace(from,to!!)) } catch(_:ClassNotFoundException) { }
            }
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    private fun normalize(s:String?)=s?.replace("com/ibm/icu/","io/github/heapy/icu4k/")
    private fun sourceField(owner:Source,name:String):Any? = Source::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(owner)
    private fun portField(owner:DateFormatSymbols,name:String):Any? = when(name) {
        "zoneStrings" -> owner.state.storedZoneStrings
        "capitalization" -> owner.state.data.capitalization
        "localPatternChars" -> owner.state.data.localPatternChars
        "timeSeparator" -> owner.state.data.timeSeparator
        "requestedLocale" -> owner.state.data.requestedLocale
        "validLocale" -> owner.state.data.validLocale
        "actualLocale" -> owner.state.data.actualLocale
        else -> owner.state.data.fields[name]
    }
    private fun shape(value:Any?):Any? = when(value) {
        is Array<*> -> value.map { shape(it) }
        is BooleanArray -> value.toList()
        is Map<*,*> -> value.entries.associate { it.key.toString() to shape(it.value) }
        is ULocale -> value.toString()
        is SourceLocale -> value.toString()
        else -> value
    }
    private fun compare(a:Source,b:DateFormatSymbols) {
        for(field in ObjectStreamClass.lookup(Source::class.java).fields) assertEquals(shape(sourceField(a,field.name)),shape(portField(b,field.name)),field.name)
        assertEquals(a.hashCode(),b.hashCode())
    }
    @Test fun exactSerialDescriptorAndRoundTrips() {
        val a=ObjectStreamClass.lookup(Source::class.java);val b=ObjectStreamClass.lookup(DateFormatSymbols::class.java)
        assertEquals(a.serialVersionUID,b.serialVersionUID)
        assertEquals(a.fields.map { it.name to normalize(it.typeString) },b.fields.map { it.name to it.typeString })
        for(id in listOf("en_US_DFSSERIAL","zh_TW_DFSSERIAL@calendar=chinese","he_IL_DFSSERIAL@calendar=hebrew")) {
            val source=Source(SourceLocale(id));val port=DateFormatSymbols(ULocale(id))
            source.setMonths(arrayOf("custom",null));port.setMonths(arrayOf("custom",null))
            source.zoneStrings=arrayOf(arrayOf("id",null,"name"));port.setZoneStrings(arrayOf(arrayOf("id",null,"name")))
            val ar=read(bytes(source)) as Source;val br=read(bytes(port)) as DateFormatSymbols
            compare(ar,br);assertEquals(port,br)
            br.setMonths(arrayOf("changed"));assertContentEquals(arrayOf("custom",null),port.getMonths())
        }
    }
    @Test fun remappedSourceStreamsWorkInBothDirections() {
        val source=Source(SourceLocale("zh_TW_DFSCROSS@calendar=chinese"));val port=DateFormatSymbols(ULocale("zh_TW_DFSCROSS@calendar=chinese"))
        source.zoneStrings=arrayOf(arrayOf("id","name"));port.setZoneStrings(arrayOf(arrayOf("id","name")))
        compare(source,read(bytes(source),"com.ibm.icu.","io.github.heapy.icu4k.") as DateFormatSymbols)
        compare(read(bytes(port),"io.github.heapy.icu4k.","com.ibm.icu.") as Source,port)
    }
    @Test fun cloneGraphsKeepArrayMapAndBooleanRowAliases() {
        val source=Source(SourceLocale("zh_DFSGRAPH@calendar=chinese"));val port=DateFormatSymbols(ULocale("zh_DFSGRAPH@calendar=chinese"))
        source.zoneStrings=arrayOf(arrayOf("id","name"));port.setZoneStrings(arrayOf(arrayOf("id","name")))
        val a=read(bytes(arrayOf(source,source.clone()))) as Array<Source>
        val b=read(bytes(arrayOf(port,port.clone()))) as Array<DateFormatSymbols>
        for(field in ObjectStreamClass.lookup(Source::class.java).fields) assertEquals(sourceField(a[0],field.name)===sourceField(a[1],field.name),portField(b[0],field.name)===portField(b[1],field.name),field.name)
        compare(a[0],b[0]);compare(a[1],b[1])
        val ac=sourceField(a[0],"capitalization") as Map<*,*>;val bc=b[0].state.data.capitalization!!
        for(x in ac.keys) for(y in ac.keys) assertEquals(ac[x]===ac[y],bc[DateFormatSymbols.CapitalizationContextUsage.valueOf(x.toString())]===bc[DateFormatSymbols.CapitalizationContextUsage.valueOf(y.toString())])
        a[1].setLeapMonthPattern("restored",0,1);b[1].setLeapMonthPattern("restored",0,1)
        assertEquals(a[0].getLeapMonthPattern(0,1),b[0].getLeapMonthPattern(0,1))
    }
    @Test fun nullFieldsAreNotRepopulatedDuringRead() {
        val source=Source(SourceLocale.US);val port=DateFormatSymbols(ULocale.US)
        for(field in ObjectStreamClass.lookup(Source::class.java).fields) {
            Source::class.java.getDeclaredField(field.name).also { it.isAccessible=true }.set(source,null)
        }
        port.state.data.fields.keys.toList().forEach { port.state.data.fields[it]=null }
        port.state.data.capitalization=null;port.state.data.localPatternChars=null;port.state.data.timeSeparator=null
        port.state.data.requestedLocale=null;port.state.data.validLocale=null;port.state.data.actualLocale=null
        val ar=read(bytes(source)) as Source;val br=read(bytes(port)) as DateFormatSymbols
        assertNull(ar.getLocale(SourceLocale.VALID_LOCALE));assertNull(br.getLocale(ULocale.VALID_LOCALE))
        assertFailsWith<NullPointerException> { ar.eras };assertFailsWith<NullPointerException> { br.getEras() }
        assertFailsWith<NullPointerException> { ar.hashCode() };assertFailsWith<NullPointerException> { br.hashCode() }
    }
    @Test fun unloadedZoneTableStaysLazyAfterRead() {
        val a=read(bytes(Source(SourceLocale.US))) as Source
        val b=read(bytes(DateFormatSymbols(ULocale.US))) as DateFormatSymbols
        assertNull(sourceField(a,"zoneStrings"));assertNull(b.state.storedZoneStrings)
        assertEquals(a.zoneStrings.map{it.toList()},b.getZoneStrings().map{it!!.toList()})
        assertNotNull(sourceField(a,"zoneStrings"));assertNotNull(b.state.storedZoneStrings)
    }
    private class Reloadable:DateFormatSymbols(ULocale.US) {
        var eraCalls=0
        override fun initEras(key:String,maps:MutableMap<String,MutableMap<String,String>>,bundle:io.github.heapy.icu4k.impl.ICUResourceBundle?,maxEra:Int):Array<String?>? {
            eraCalls++;return super.initEras(key,maps,bundle,maxEra)
        }
        fun reload()=initializeData(ULocale.JAPAN,null,"japanese")
    }
    @Test fun restoredStateRebindsCallbacksWithoutRunningConstructors() {
        val original=Reloadable()
        val restored=read(bytes(original)) as Reloadable
        assertEquals(0,restored.eraCalls)
        restored.reload()
        assertEquals(3,restored.eraCalls);assertEquals(0,original.eraCalls)
        assertContentEquals(Source(SourceLocale("ja@calendar=japanese")).eras,restored.getEras())
        assertEquals(2,original.getEras().size)
    }

}
