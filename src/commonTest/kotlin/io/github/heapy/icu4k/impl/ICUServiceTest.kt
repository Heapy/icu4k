package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICUServiceTest {
    @Test fun registryVisibilitySnapshotsAndInvalidation() {
        val service = ICUService("test")
        val a = service.registerObject("first", "a")
        assertEquals("first", service.get("a"))
        val old = service.getVisibleIDs()
        val b = service.registerObject("second", "a", false)
        assertEquals("second", service.get("a")); assertTrue(service.getVisibleIDs().isEmpty())
        assertEquals(setOf("a"), old)
        assertTrue(service.unregisterFactory(b)); assertEquals("first", service.get("a"))
        assertEquals("a", service.getDisplayName("a")); assertNull(service.getDisplayName("b"))
        service.factories().clear(); assertEquals("first", service.get("a"))
        val actual = arrayOf<String?>("unchanged")
        assertNull(service.get("missing", actual)); assertEquals("unchanged", actual[0])
        assertEquals("first", service.get("a", actual)); assertEquals("a", actual[0])
        assertFailsWith<IndexOutOfBoundsException> { service.get("a", emptyArray()) }
        assertTrue(service.unregisterFactory(a)); assertTrue(service.isDefault())
        assertNull(service.get(null)); assertFailsWith<NullPointerException> { service.get(null, actual) }
    }
    @Test fun factoryDelegationCacheAndFailureRelease() {
        val service = ICUService()
        service.registerObject("base", "a")
        var calls = 0
        val wrapper = object : ICUService.Factory {
            override fun create(key: ICUService.Key?, service: ICUService?): Any? {
                calls++
                return "wrapped:" + service!!.getKey(key, null, this)
            }
            override fun updateVisibleIDs(result: MutableMap<String, ICUService.Factory>) { }
            override fun getDisplayName(id: String?, locale: io.github.heapy.icu4k.util.ULocale?): String? = null
        }
        service.registerFactory(wrapper)
        assertEquals("wrapped:base", service.get("a")); assertEquals("wrapped:base", service.get("a")); assertEquals(1, calls)
        assertTrue(service.unregisterFactory(wrapper)); assertEquals("base", service.get("a"))
        assertFailsWith<IllegalStateException> { service.getKey(ICUService.Key("a"), null, wrapper) }
        val failure = IllegalStateException("factory")
        val broken = object : ICUService.SimpleFactory("unused", "a") {
            override fun create(key: ICUService.Key?, service: ICUService?): Any? = throw failure
        }
        service.registerFactory(broken)
        assertSame(failure, assertFailsWith<IllegalStateException> { service.get("a") })
        assertTrue(service.unregisterFactory(broken)); assertEquals("base", service.get("a"))
        service.reset(); assertTrue(service.factories().isEmpty())
    }

}
