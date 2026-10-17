package io.github.heapy.icu4k.text

import com.ibm.icu.text.Transliterator as Source
import java.lang.reflect.InvocationTargetException
import java.util.function.Supplier
import kotlin.test.*

class TransliteratorRegistryCoreOracleTest {
    init { TransliteratorIDParser.registerSpecialInverse("Null", "Null", false) }
    private val host = RegistryFixtureHost
    private val type = Class.forName("com.ibm.icu.text.TransliteratorRegistry")
    private fun sourceRegistry(): Any = type.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
    private fun invoke(target: Any, name: String, signature: Array<Class<*>>, vararg args: Any?): Any? {
        try { return type.getDeclaredMethod(name,*signature).apply { isAccessible=true }.invoke(target,*args) }
        catch(e: InvocationTargetException) { throw e.targetException }
    }
    private fun putResource(target: Any, id: String, supplier: Supplier<String?>, direction: Int) = invoke(target,"put",arrayOf(String::class.java,Supplier::class.java,Int::class.javaPrimitiveType!!,Boolean::class.javaPrimitiveType!!),id,supplier,direction,true)
    private fun get(target: Any, id: String, alias: StringBuilder): Source? = invoke(target,"get",arrayOf(String::class.java,StringBuilder::class.java),id,alias) as Source?

    @Test fun parsedEntryTransitionsCachingAndCompounds() {
        val rules = listOf("", "# empty", "a > b;", "::[a]; a > b;", "::Null;", "::[a]; ::Null;", "a > b; ::Null; b > c;", "::Null; a > b;", "a > b; ::Null;", "::[ab]; a > b; ::Null; b > cc;")
        for(rule in rules) for(direction in 0..1) {
            val source = sourceRegistry(); val port=TransliteratorRegistryCore(host)
            var sourceCalls=0; var portCalls=0
            putResource(source,"X-Y",Supplier { sourceCalls++; rule },direction)
            port.putResourceSupplier("X-Y",{ portCalls++; rule },direction,true)
            repeat(2) {
                val sa=StringBuilder("prefix:"); val pa=StringBuilder("prefix:")
                val s=get(source,"x-y",sa); val p=port.get("x-y",pa)
                assertEquals(sa.toString(),pa.toString(),"$rule/$direction")
                assertEquals(s?.getID(),p?.getID(),rule)
                assertEquals((s?.getFilter() as com.ibm.icu.text.UnicodeSet?)?.toPattern(false),p?.filterValue?.toPattern(false),rule)
                for(escape in listOf(false,true)) assertEquals(s?.toRules(escape),p?.rules(escape),rule)
                if(s!=null && p!=null) for(input in listOf("", "abc", "aabbcc")) {
                    val text=ReplaceableString(input); p.transliterate(text,0,text.length())
                    assertEquals(s.transliterate(input),text.toString(),rule)
                }
            }
            assertEquals(sourceCalls,portCalls,rule); assertEquals(1,portCalls,rule)
        }
    }
    @Test fun nullRetryExceptionsAndAliasAppend() {
        for(policy in 0..3) {
            val source=sourceRegistry(); val port=TransliteratorRegistryCore(host)
            var sc=0; var pc=0
            fun supply(call: Int): String? = when(policy) { 0->null; 1->if(call==1)throw IllegalArgumentException("supplier") else "a>b;"; 2->if(call==1)throw ClassCastException("supplier") else "a>b;"; else->"a > ; >;" }
            putResource(source,"X-Y",Supplier { supply(++sc) },0)
            port.putResourceSupplier("X-Y",{ supply(++pc) },0,true)
            fun result(block: ()->Any?): String = try { block(); "ok" } catch(e:Throwable) { e.javaClass.simpleName }
            repeat(3) {
                val sa=StringBuilder("prefix:"); val pa=StringBuilder("prefix:")
                assertEquals(result{get(source,"X-Y",sa)},result{port.get("X-Y",pa)},"policy $policy")
                assertEquals(sa.toString(),pa.toString()); assertEquals(sc,pc)
            }
        }
        val entry=TransliteratorRegistryCore.ResourceEntry({ null },0)
        assertNull(entry.getResource()); assertNull(entry.getResource())
    }
    @Test fun factoryClassAndStoredInstanceLifecycle() {
        val port=TransliteratorRegistryCore(host)
        val calls=mutableListOf<String>()
        port.putFactory("Latin-X",{ id -> calls.add(id); RegistryFixtureFunction(id) },true)
        assertEquals("en_US-X",port.get("en_US-X",StringBuilder())!!.getID())
        assertEquals(listOf("en_US-X"),calls)
        var count=0
        port.putClass("A-B",{ count++; RegistryFixtureFunction("constructed") },true)
        assertNotSame(port.get("A-B",StringBuilder()),port.get("A-B",StringBuilder())); assertEquals(2,count)
        val instance=RegistryFixtureFunction("shared")
        port.putInstance("C-D",instance,true)
        assertSame(instance,port.get("C-D",StringBuilder()))
        port.putAlias("E-F","Latin-Greek",true)
        val alias=StringBuilder("before:")
        assertNull(port.get("E-F",alias)); assertEquals("before:Latin-Greek",alias.toString())
    }
    internal class ConstructedFunction : TransliterationFunction by RegistryFixtureFunction("constructed")
    internal abstract class AbstractFunction : TransliterationFunction
    internal class PrivateFunction private constructor() : TransliterationFunction by RegistryFixtureFunction("private")
    internal class ThrowingFunction : TransliterationFunction by RegistryFixtureFunction("throw") {
        init { throw IllegalStateException("constructor") }
    }
    class ConstructedSource : Source("constructed",null) {
        override fun handleTransliterate(text: com.ibm.icu.text.Replaceable, position: Source.Position, incremental: Boolean) { position.start=position.limit }
    }
    abstract class AbstractSource : Source("abstract",null)
    class PrivateSource private constructor() : Source("private",null) {
        override fun handleTransliterate(text: com.ibm.icu.text.Replaceable, position: Source.Position, incremental: Boolean) { position.start=position.limit }
    }
    class ThrowingSource : Source("throw",null) {
        init { throw IllegalStateException("constructor") }
        override fun handleTransliterate(text: com.ibm.icu.text.Replaceable, position: Source.Position, incremental: Boolean) { position.start=position.limit }
    }
    @Test fun reflectiveClassConstructionMatchesSource() {
        for((sourceClass,portClass) in listOf(ConstructedSource::class.java to ConstructedFunction::class.java,
            AbstractSource::class.java to AbstractFunction::class.java, PrivateSource::class.java to PrivateFunction::class.java,
            ThrowingSource::class.java to ThrowingFunction::class.java)) {
            val source=sourceRegistry()
            val port=TransliteratorRegistryCore<TransliterationFunction>(object : TransliteratorRegistryCore.Host<TransliterationFunction> {
                override val parserFactory=host.parserFactory
                override fun ruleBased(id:String,data:TransliterationData,filter:UnicodeSet?)=host.ruleBased(id,data,filter)
                override fun compound(elements:List<TransliterationFunction>,anonymousPasses:Int):TransliterationFunction=error("unused")
                override fun getInstance(id:String)=host.getInstance(id)
                override fun setID(instance:TransliterationFunction,id:String)=Unit
                override fun setFilter(instance: TransliterationFunction, filter: UnicodeSet) { instance.setFilter(filter) }
        override fun cloneStored(instance:TransliterationFunction)=instance
            })
            invoke(source,"put",arrayOf(String::class.java,Class::class.java,Boolean::class.javaPrimitiveType!!),"X-Y",sourceClass,true)
            port.putClass("X-Y",portClass,true)
            fun result(block:()->String?):String=try { "ok:"+block() } catch(e:Throwable) { e.javaClass.simpleName+":"+e.message }
            repeat(2) { assertEquals(result { get(source,"X-Y",StringBuilder())?.getID() },result { port.get("X-Y",StringBuilder())?.getID() },sourceClass.name) }
        }
    }

}
