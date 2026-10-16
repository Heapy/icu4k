@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateFormatSymbols as Source
import com.ibm.icu.impl.ICUResourceBundle as SourceBundle
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.random.Random
import kotlin.test.*

class DateFormatSymbolsStateCoreOracleTest {
    private val arrayType=Array<String>::class.java
    private val arrays=Source::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type==arrayType }.onEach { it.isAccessible=true }
    private fun data(id: String="en_US",type: String="gregorian")=DateFormatSymbolsDataCore(object: DateFormatSymbolsDataCore.Owner {
        override fun className()=Source::class.java.name
        override fun currentTimeMillis()=System.currentTimeMillis()
        override fun defaultZoneOffset(time: Long)=TimeZone.getDefault().getOffset(time)
    },ICUResourceBundleReader.embeddedLoader).also { it.initialize(ULocale(id),null,type) }
    private fun pair(id: String="en_US",type: String="gregorian"): Pair<Source,DateFormatSymbolsStateCore> {
        val ctor=Source::class.java.getDeclaredConstructor(SourceLocale::class.java,SourceBundle::class.java,String::class.java).apply { isAccessible=true }
        return ctor.newInstance(SourceLocale(id),null,type) to DateFormatSymbolsStateCore(data(id,type))
    }
    private fun read(source: Source,name: String): Any?=Source::class.java.getDeclaredField(name).apply { isAccessible=true }.get(source)
    private fun normalized(value: Any?): Any?=when(value) { is Array<*> -> value.map { normalized(it) }; else -> value }
    private fun outcome(action: ()->Any?): Any?=try { "ok" to normalized(action()) } catch(error: Throwable) {
        val cause=if(error is InvocationTargetException) error.targetException else error
        if(cause is VirtualMachineError) throw cause
        if(cause !is Exception) throw cause
        "error" to when(cause) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> cause.javaClass.simpleName to cause.message }
    }
    private fun check(a: Source,b: DateFormatSymbolsStateCore) {
        for(field in arrays) assertEquals(normalized(field.get(a)),normalized(b.data.fields[field.name]),field.name)
        assertEquals(normalized(read(a,"zoneStrings")),normalized(b.storedZoneStrings))
        assertEquals(a.timeSeparatorString,b.getTimeSeparatorString()); assertEquals(a.localPatternChars,b.getLocalPatternChars())
        assertEquals(a.hashCode(),b.hashCodeState())
    }
    private fun call(a: Source,b: DateFormatSymbolsStateCore,name: String,types: Array<Class<*>>,arguments: Array<Any?>) {
        val am=Source::class.java.getMethod(name,*types); val bm=DateFormatSymbolsStateCore::class.java.getMethod(name,*types)
        assertEquals(outcome { am.invoke(a,*arguments) },outcome { bm.invoke(b,*arguments) },"$name/${arguments.toList()}")
        check(a,b)
    }
    @Test fun contextsWidthsNullsCopyingAndMutationMatchSource() {
        for((id,type) in listOf("en_US" to "gregorian","zh_CN" to "chinese","he_IL" to "hebrew")) {
            val(a,b)=pair(id,type)
            for(context in listOf(-1,0,1,2,3,Int.MIN_VALUE,Int.MAX_VALUE)) for(width in listOf(-1,0,1,2,3,4,Int.MIN_VALUE,Int.MAX_VALUE)) {
                for(name in listOf("Months","Weekdays","Quarters","YearNames","ZodiacNames","AmPmStrings")) {
                    call(a,b,"get$name",arrayOf(Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!),arrayOf(context,width))
                    for(value in listOf<Array<String?>?>(null,emptyArray(),arrayOf("one",null,"three"))) {
                        call(a,b,"set$name",arrayOf(arrayType,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!),arrayOf(value,context,width))
                        value?.let { if(it.isNotEmpty()) it[0]="input mutated" }; check(a,b)
                    }
                }
                call(a,b,"getLeapMonthPattern",arrayOf(Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!),arrayOf(context,width))
                call(a,b,"setLeapMonthPattern",arrayOf(String::class.java,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!),arrayOf("$context/$width",context,width))
            }
            for(name in listOf("Eras","EraNames","NarrowEras","Months","ShortMonths","Weekdays","ShortWeekdays","AmPmStrings")) {
                for(value in listOf<Array<String?>?>(null,emptyArray(),arrayOf("a",null,"c"))) {
                    call(a,b,"set$name",arrayOf(arrayType),arrayOf(value)); value?.let { if(it.isNotEmpty()) it[0]="changed" }; check(a,b)
                    call(a,b,"get$name",emptyArray(),emptyArray())
                    val x=Source::class.java.getMethod("get$name").invoke(a) as Array<*>
                    @Suppress("UNCHECKED_CAST") val y=DateFormatSymbolsStateCore::class.java.getMethod("get$name").invoke(b) as Array<String?>
                    if(x.isNotEmpty()) { (x as Array<Any?>)[0]="output mutated"; y[0]="output mutated" }; check(a,b)
                }
            }
        }
    }
    @Test fun missingFieldsLegacyShortWeekdaysAndMalformedLeapArrays() {
        val(a,b)=pair("zh_CN","chinese")
        for(field in arrays) { field.set(a,null); b.data.fields[field.name]=null }
        for(name in listOf("Months","Weekdays","Quarters","YearNames","ZodiacNames","AmPmStrings")) for(c in -1..3) for(w in -1..4)
            call(a,b,"get$name",arrayOf(Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!),arrayOf(c,w))
        for(size in 0..7) {
            val values=Array<String?>(size) { "slot$it" }
            Source::class.java.getDeclaredField("leapMonthPatterns").apply { isAccessible=true }.set(a,values.copyOf()); b.data.fields["leapMonthPatterns"]=values.copyOf()
            for(c in -1..3) for(w in -1..4) {
                call(a,b,"getLeapMonthPattern",arrayOf(Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!),arrayOf(c,w))
                call(a,b,"setLeapMonthPattern",arrayOf(String::class.java,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!),arrayOf(null,c,w))
            }
        }
        a.setShortWeekdays(arrayOf("legacy")); b.setShortWeekdays(arrayOf("legacy"))
        assertContentEquals(a.getWeekdays(0,3),b.getWeekdays(0,3))
    }
    @Test fun shallowCopiesZoneDefensesEqualityOmissionsAndHashMatchSource() {
        val(a,b)=pair("zh_CN","chinese")
        val ac=a.clone(); val bc=DateFormatSymbolsStateCore(data()).also { it.initializeFrom(b) }
        val random=Random(78317)
        val names=listOf("eras","months","quarters","shortQuarters","leapMonthPatterns","shortYearNames","shortZodiacNames","abbreviatedDayPeriods")
        for(name in names) {
            val field=Source::class.java.getDeclaredField(name).apply { isAccessible=true }
            assertSame(field.get(a),field.get(ac)); assertSame(b.data.fields[name],bc.data.fields[name])
        }
        for(step in 0..99) {
            val name=names[random.nextInt(names.size)]; val values=if(step%7==0) null else arrayOf<String?>("$step",null)
            Source::class.java.getDeclaredField(name).apply { isAccessible=true }.set(ac,values?.copyOf()); bc.data.fields[name]=values?.copyOf()
            assertEquals(a==ac,b.equalsState(bc) { SourceLocale(it.toString()).displayName },"equals/$name/$step")
            assertEquals(ac.hashCode(),bc.hashCodeState())
        }
        val zones=arrayOf<Array<String?>?>(arrayOf("id","standard",null),emptyArray())
        a.zoneStrings=zones; b.setZoneStrings(zones)
        zones[0]!![0]="input"; check(a,b)
        val output=a.zoneStrings; val target=b.copyZoneStrings(); output[0][0]="output"; target[0]!![0]="output"; check(a,b)
        for(value in listOf<Array<Array<String?>?>?>(null,arrayOf(null))) {
            assertEquals(outcome { a.setZoneStrings(value) },outcome { b.setZoneStrings(value) }); check(a,b)
        }
        for(value in listOf(null,"","pattern")) { a.localPatternChars=value; b.setLocalPatternChars(value); a.timeSeparatorString=value; b.setTimeSeparatorString(value); check(a,b) }
    }
    @Test fun equalityTestsEveryFieldIndependentlyAndDeprecatedLocaleAliases() {
        for(field in arrays) {
            val(a,b)=pair("zh_CN","chinese"); val ac=a.clone(); val bc=DateFormatSymbolsStateCore(data()).also { it.initializeFrom(b) }
            val value=arrayOf<String?>("changed",null)
            field.set(ac,value.copyOf()); bc.data.fields[field.name]=value.copyOf()
            assertEquals(a==ac,b.equalsState(bc) { SourceLocale(it.toString()).displayName },field.name)
            assertEquals(a.hashCode(),b.hashCodeState()); assertEquals(ac.hashCode(),bc.hashCodeState())
        }
        for((first,second) in listOf("iw" to "he","in" to "id","ji" to "yi","tl" to "fil","no_NO_NY" to "nn_NO")) {
            val(a,b)=pair(first); val(c,d)=pair(second)
            assertEquals(a==c,b.equalsState(d) { SourceLocale(it.toString()).displayName },"$first/$second")
            assertEquals(a.hashCode(),b.hashCodeState()); assertEquals(c.hashCode(),d.hashCodeState())
        }
    }
}
