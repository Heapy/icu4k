package io.github.heapy.icu4k.text

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class TransliteratorRegistryStoreOracleTest {
    private val type = Class.forName("com.ibm.icu.text.TransliteratorRegistry")
    private val java = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    private val kotlin = TransliteratorRegistryStore()
    private fun call(name: String, vararg args: Any): Any? {
        val signature = args.map { if (it is Boolean) Boolean::class.javaPrimitiveType!! else String::class.java }.toTypedArray()
        return type.getDeclaredMethod(name, *signature).apply { isAccessible = true }.invoke(java, *args)
    }
    private fun put(id: String, value: String, visible: Boolean) { call("put",id,value,visible); kotlin.put(id,value,visible) }
    private fun remove(id: String) { call("remove",id); kotlin.remove(id) }
    private fun javaValues(name: String, vararg args: String): List<String> {
        @Suppress("UNCHECKED_CAST")
        val e = call(name,*args) as java.util.Enumeration<String>
        return buildList { while(e.hasMoreElements()) add(e.nextElement()) }
    }
    private fun values(e: io.github.heapy.icu4k.util.Enumeration<String>): List<String> = buildList { while(e.hasMoreElements()) add(e.nextElement()) }
    private fun compareGraph() {
        assertEquals(javaValues("getAvailableIDs"), values(kotlin.getAvailableIDs()))
        val sources = javaValues("getAvailableSources").toSet()
        assertEquals(sources, values(kotlin.getAvailableSources()).toSet())
        for(source in sources) {
            val targets = javaValues("getAvailableTargets",source).toSet()
            assertEquals(targets, values(kotlin.getAvailableTargets(source)).toSet())
            for(target in targets) assertEquals(javaValues("getAvailableVariants",source,target), values(kotlin.getAvailableVariants(source,target)))
        }
    }
    private fun findJava(id: String): Array<*>? = call("find",id) as Array<*>?
    private fun describeJava(entry: Any?): String? {
        if(entry == null) return null
        fun field(name: String) = entry.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(entry)
        return if(entry.javaClass.simpleName == "AliasEntry") "alias:${field("alias")}" else "locale:${field("direction")}:${field("rule")}"
    }
    private fun describeKotlin(entry: Any?): String? = when(entry) {
        null -> null
        is TransliteratorRegistryStore.LocaleEntry -> "locale:${entry.direction}:${entry.rule}"
        else -> "alias:$entry"
    }
    private fun compareFind(id: String) = assertEquals(describeJava(findJava(id)?.get(0)), describeKotlin(kotlin.find(id)?.get(0)), id)

    @Test fun registrationVisibilityCaseFoldingAndRemoval() {
        val ids = listOf("Latin-Greek", "latin-greek/foo", "LATIN-GREEK/BAR", "Any-X", "X", "Straße-Y/v", "STRASSE-y/V", "Latn-Grek/", "el-Latin", "Latin-el", "en_US-xx_YY/var")
        val random = Random(1903)
        repeat(120) { i ->
            val id = ids[random.nextInt(ids.size)]
            if(random.nextInt(4) == 0) remove(id) else put(id,"entry$i",random.nextBoolean())
            compareGraph()
            for(query in ids) compareFind(query)
        }
    }
    @Test fun exactVariantsAndLocaleScriptFallback() {
        put("Latin-X", "script", true)
        put("en-X", "language", true)
        put("en_US-X/v", "variant", true)
        put("en-X/v", "variantDoesNotFallback", true)
        put("Latin-Greek", "bothScripts", true)
        for(id in listOf("en_US-X/v", "en_US_POSIX-X/v", "en_US-X/missing", "en_GB-X/v", "fr-X", "en-el", "en_US-el_GR", "Latn-Grek")) compareFind(id)
        for(id in listOf("el-Latin", "el_GR-Latin", "Greek-el", "Latin-el", "Any-el", "el-Latin/UNGEGN", "el-Latin/missing")) {
            compareFind(id)
            compareFind(id) // Static locale entries are cached, with shared wrappers.
        }
        compareGraph()
    }
    @Test fun lazyEnumerationFailureModes() {
        @Suppress("UNCHECKED_CAST")
        fun javaEnum(name: String, vararg args: String) = call(name,*args) as java.util.Enumeration<String>
        fun failure(block: () -> Any?): String = try { block(); "ok" } catch(e: InvocationTargetException) { e.targetException.javaClass.simpleName } catch(e: Throwable) { e.javaClass.simpleName }
        put("A-B/v", "one", true)
        val idsJ = javaEnum("getAvailableIDs"); val idsK = kotlin.getAvailableIDs()
        put("A-B/v", "replacement", true)
        assertEquals(idsJ.nextElement(), idsK.nextElement())
        put("A-B", "default", true)
        assertEquals(idsJ.hasMoreElements(), idsK.hasMoreElements())
        assertEquals(failure { idsJ.nextElement() }, failure { idsK.nextElement() })
        val variantsJ = javaEnum("getAvailableVariants","A","B"); val variantsK = kotlin.getAvailableVariants("A","B")
        remove("A-B"); remove("A-B/v")
        assertEquals(variantsJ.hasMoreElements(), variantsK.hasMoreElements())
        assertEquals(failure { variantsJ.nextElement() }, failure { variantsK.nextElement() })
        val missingJ = javaEnum("getAvailableTargets","missing"); val missingK = kotlin.getAvailableTargets("missing")
        assertFalse(missingJ.hasMoreElements()); assertFalse(missingK.hasMoreElements())
        assertEquals(failure { missingJ.nextElement() }, failure { missingK.nextElement() })
    }
}
