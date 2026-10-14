package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import kotlin.test.*

class UResourceBundleTest {
    private class Bundle(locale: String) : ICUResourceBundle(WholeBundle("test/bundle", locale,
        ICUResourceBundleReader.getReader(ICUData.ICU_BRKITR_BASE_NAME, "root")!!)) {
        val children = linkedMapOf<String, UResourceBundle>()
        val requested = mutableListOf<UResourceBundle>()
        fun attach(parent: Bundle) { setParent(parent) }
        override fun getType(): Int = TABLE
        override fun getSize(): Int = children.size
        override fun handleGet(aKey: String, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle? {
            this.requested.add(requested)
            return children[aKey]
        }
        override fun handleGet(index: Int, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle? = children.values.elementAtOrNull(index)
        override fun handleKeySet(): Set<String> = children.keys
    }
    private class Scalar(container: Bundle, key: String, private val value: String?) : ICUResourceBundle(container, key) {
        override fun getType(): Int = if (value == null) INT else STRING
        override fun getString(): String = value ?: super.getString()
    }
    private class ArrayValue : UResourceBundle() {
        var fail = false
        override fun getULocale(): ULocale = ULocale.ROOT
        override fun getLocaleID(): String = "root"
        override fun getBaseName(): String = "test/bundle"
        override fun getParent(): UResourceBundle? = null
        override fun getType(): Int = ARRAY
        override fun handleGetStringArray(): Array<String> {
            if (fail) throw UResourceTypeMismatchException("")
            return arrayOf("a", "b")
        }
    }
    @Test fun lookupFallbackObjectsKeysAndMissingResources() {
        val parent = Bundle("root")
        val child = Bundle("en")
        parent.children["z"] = Scalar(parent, "z", "parent")
        child.children["a"] = Scalar(child, "a", "child")
        child.attach(parent)
        assertEquals("parent", child["z"].getString())
        assertSame(child, parent.requested.last())
        assertEquals("parent", child.getString("z"))
        assertSame(child, parent.requested.last())
        assertTrue(child.containsKey("z")); assertFalse(child.containsKey("missing"))
        assertEquals(listOf("a", "z"), child.keySet().toList())
        assertSame(child.keySet(), child.keySet())
        val keys = child.getKeys()
        assertTrue(keys.hasMoreElements()); assertEquals("a", keys.nextElement()); assertEquals("z", keys.nextElement())
        assertFalse(keys.hasMoreElements()); assertFailsWith<NoSuchElementException> { keys.nextElement() }
        assertFailsWith<UnsupportedOperationException> { (child.keySet() as MutableSet<String>).clear() }
        val failure = assertFailsWith<MissingResourceException> { child["missing"] }
        assertEquals("missing", failure.getKey())
        assertEquals("Can't find resource for bundle test/bundle/en.res, key missing", failure.message)
        val array = ArrayValue()
        child.children["array"] = array
        assertContentEquals(arrayOf("a", "b"), child.getStringArray("array"))
        array.fail = true
        assertSame(array, child.getObject("array"))
        val scalar = child["a"]
        assertEquals("child", scalar.getObject("any-key"))
        assertTrue(scalar.keySet().isEmpty())
    }
    @Test fun iteratorCapturesSizeAdvancesOnFailureAndResets() {
        val root = Bundle("root")
        root.children["a"] = Scalar(root, "a", "first")
        root.children["b"] = Scalar(root, "b", null)
        root.children["c"] = Scalar(root, "c", "third")
        val iterator = root.getIterator()
        root.children["d"] = Scalar(root, "d", "fourth")
        assertEquals("first", iterator.nextString())
        assertFailsWith<UResourceTypeMismatchException> { iterator.nextString() }
        assertEquals("third", iterator.nextString())
        assertFalse(iterator.hasNext()); assertFailsWith<NoSuchElementException> { iterator.next() }
        iterator.reset(); assertSame(root[0], iterator.next())
        assertTrue(iterator.hasNext())
    }
    @Test fun sharedWholeBundleIdentityAndDefaultScalarContracts() {
        val root = Bundle("root")
        val a = Scalar(root, "a", "one"); val b = Scalar(root, "b", "two")
        assertEquals(a, b); assertEquals<ICUResourceBundle>(root, a); assertEquals(42, a.hashCode())
        assertTrue(a.isRoot()); assertEquals("a", a.getKey()); assertEquals(ULocale("root"), a.getULocale())
        assertNull(a.getVersion()); assertEquals(1, a.getSize())
        assertFailsWith<UResourceTypeMismatchException> { a.getInt() }
        assertFailsWith<UResourceTypeMismatchException> { a.getUInt() }
        assertFailsWith<UResourceTypeMismatchException> { a.getBinary() }
        assertFailsWith<UResourceTypeMismatchException> { a.getBinary(null) }
        assertFailsWith<UResourceTypeMismatchException> { a.getIntVector() }
        assertFailsWith<UResourceTypeMismatchException> { a.getStringArray() }
        assertNull(a.getParent())
        assertFalse(Bundle("en").isRoot())
    }
}
