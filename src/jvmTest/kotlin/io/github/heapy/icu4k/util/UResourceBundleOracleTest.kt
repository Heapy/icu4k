package io.github.heapy.icu4k.util

import kotlin.test.*
import com.ibm.icu.util.UResourceBundle as JBundle
import com.ibm.icu.util.ULocale as JLocale

class UResourceBundleOracleTest {
    private class Original(private val id: String, private val text: String? = null) : JBundle() {
        val children = linkedMapOf<String, Original>()
        private var ancestor: Original? = null
        val requests = mutableListOf<JBundle>()
        fun attach(value: Original) { ancestor = value; setParent(value) }
        override fun getULocale(): JLocale = JLocale(id)
        override fun getLocaleID(): String = id
        override fun getBaseName(): String = "test/bundle"
        override fun getParent(): JBundle? = ancestor
        override fun getType(): Int = if (text == null) TABLE else STRING
        override fun getString(): String = text ?: super.getString()
        override fun getSize(): Int = children.size
        override fun handleGet(key: String, aliasesVisited: java.util.HashMap<String, String>?, requested: JBundle): JBundle? {
            requests.add(requested); return children[key]
        }
        override fun handleGet(index: Int, aliasesVisited: java.util.HashMap<String, String>?, requested: JBundle): JBundle? = children.values.elementAtOrNull(index)
        override fun handleKeySet(): Set<String> = children.keys
    }
    private class Port(private val id: String, private val text: String? = null) : UResourceBundle() {
        val children = linkedMapOf<String, Port>()
        val requests = mutableListOf<UResourceBundle>()
        fun attach(value: Port) { setParent(value) }
        override fun getULocale(): ULocale = ULocale(id)
        override fun getLocaleID(): String = id
        override fun getBaseName(): String = "test/bundle"
        override fun getParent(): UResourceBundle? = parentBundle
        override fun getType(): Int = if (text == null) TABLE else STRING
        override fun getString(): String = text ?: super.getString()
        override fun getSize(): Int = children.size
        override fun handleGet(aKey: String, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle? {
            requests.add(requested); return children[aKey]
        }
        override fun handleGet(index: Int, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle? = children.values.elementAtOrNull(index)
        override fun handleKeySet(): Set<String> = children.keys
    }
    @Test fun baseLookupFallbackAndIteratorFailureStateMatchOriginal() {
        val jp = Original("root"); val j = Original("en").also { it.attach(jp) }
        val kp = Port("root"); val k = Port("en").also { it.attach(kp) }
        for (name in listOf("b", "a")) { jp.children[name] = Original("root", name); kp.children[name] = Port("root", name) }
        j.children["local"] = Original("en", "local"); k.children["local"] = Port("en", "local")
        for (key in listOf("a", "b", "local")) {
            assertEquals(j[key].string, k[key].getString())
            assertEquals(j.getObject(key), k.getObject(key))
            assertEquals(j.containsKey(key), k.containsKey(key))
        }
        assertSame(j, jp.requests.last()); assertSame(k, kp.requests.last())
        assertEquals(j.keySet().toList(), k.keySet().toList())
        val je = assertFailsWith<java.util.MissingResourceException> { j["missing"] }
        val ke = assertFailsWith<MissingResourceException> { k["missing"] }
        assertEquals(je.message, ke.message); assertEquals(je.key, ke.getKey())
        val ji = j.iterator; val ki = k.getIterator()
        assertFailsWith<ClassCastException> { ji.nextString() }; assertFailsWith<ClassCastException> { ki.nextString() }
        assertEquals(ji.hasNext(), ki.hasNext())
        ji.reset(); ki.reset()
        assertEquals(ji.next().string, ki.next().getString())
        assertEquals(ji.hasNext(), ki.hasNext())
    }
}
