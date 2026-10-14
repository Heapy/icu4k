package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class ResourceBundleWrapperTest {
    @Test fun propertiesAndClassObjectsKeepParentsKeysAndOriginalTypedBoundaries() {
        val base = "icu4k.test.wrapper.common"
        val files = mapOf("${base.replace('.', '/')}.properties" to "word=root\nrootOnly=fallback\n".encodeToByteArray())
        val loader = object : ICUResourceLoader {
            override fun getData(fullName: String): BinaryBuffer? = files[fullName]?.let { BinaryBuffer(it) }
            override fun loadJavaBundleClass(name: String): JavaBundleClassResult = if (name == "${base}_en")
                JavaBundleClassResult.Found(MapJavaResourceBundle(linkedMapOf("word" to "class", "arr" to arrayOf("a", "b"), "number" to 37), "fixture.ClassBundle"))
                else JavaBundleClassResult.LoadProperties
        }
        val bundle = ResourceBundleWrapper.getBundleInstance(base, "en_US", loader, true)
        assertEquals("en", bundle.getULocale().getName())
        assertEquals("class", bundle.getString("word")); assertEquals("fallback", bundle.getString("rootOnly"))
        assertContentEquals(arrayOf("a", "b"), bundle.getStringArray("arr")); assertEquals(37, bundle.getObject("number"))
        val keys = bundle.getKeys(); val names = mutableListOf<String>()
        while (keys.hasMoreElements()) names.add(keys.nextElement())
        assertEquals(listOf("word", "arr", "number"), names.take(3)); assertEquals(setOf("word", "arr", "number", "rootOnly"), names.toSet())
        assertTrue(bundle.keySet().isEmpty()); assertFalse(bundle.containsKey("word"))
        assertEquals(UResourceBundle.NONE, bundle.getType()); assertEquals(1, bundle.getSize())
        assertFailsWith<UResourceTypeMismatchException> { bundle.getString() }
        assertFailsWith<MissingResourceException> { bundle["word"] }
        assertEquals("", bundle.getParent()!!.getULocale().getName())
        assertSame(bundle, ResourceBundleWrapper.getBundleInstance(base, "en_US", ICUResourceLoader { error("cached") }, true))
    }
    @Test fun rejectedClassDoesNotReadPropertiesOrReturnParentAndMissingLanguageRejectsRootInDirectMode() {
        val base = "icu4k.test.wrapper.rejected"
        var childPropertyReads = 0
        val loader = object : ICUResourceLoader {
            override fun getData(fullName: String): BinaryBuffer? {
                if (fullName.endsWith("_en.properties")) ++childPropertyReads
                return BinaryBuffer("word=property\n".encodeToByteArray())
            }
            override fun loadJavaBundleClass(name: String): JavaBundleClassResult = if (name.endsWith("_en"))
                throw IllegalStateException("constructor") else JavaBundleClassResult.LoadProperties
        }
        val failure = assertFailsWith<MissingResourceException> { ResourceBundleWrapper.getBundleInstance(base, "en", loader, true) }
        assertEquals("Could not find the bundle ${base}_en", failure.message); assertEquals("", failure.getKey())
        assertEquals(0, childPropertyReads)
        val rootOnly = ICUResourceLoader { name -> if (name.endsWith("_zz.properties")) null else BinaryBuffer("word=root".encodeToByteArray()) }
        assertFailsWith<MissingResourceException> { ResourceBundleWrapper.getBundleInstance("icu4k/test/wrapper/root-only", "zz", rootOnly, true) }
    }
    @Test fun commonPropertiesReadEscapesEncodingAndContinueAcrossLines() {
        val text = "#comment\nkey\\ with\\ spaces : café\ncontinued=left\\\r\n \t right\nescaped=\\u03b1\\n\\t\ndup=old\ndup:new\n"
        val base = "icu4k.test.wrapper.properties"
        val bundle = ResourceBundleWrapper.getBundleInstance(base, "", ICUResourceLoader { BinaryBuffer(text.encodeToByteArray()) }, true)
        assertEquals("café", bundle.getString("key with spaces")); assertEquals("leftright", bundle.getString("continued"))
        assertEquals("α\n\t", bundle.getString("escaped")); assertEquals("new", bundle.getString("dup"))
        val latin = JavaPropertiesBundle.read(byteArrayOf(97, 61, -1), null)
        assertEquals("ÿ", latin.getObject("a"))
        assertFails { JavaPropertiesBundle.read(byteArrayOf(97, 61, -1), "UTF-8") }
    }
}
