package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICUDebugTest {
    @Test fun debugParametersRetainSubstringAndHelpSemantics() {
        val output = mutableListOf<String>()
        val absent = DebugSettings(null, output::add)
        assertFalse(absent.enabled()); assertFalse(absent.enabled("")); assertEquals("false", absent.value("x")); assertTrue(output.isEmpty())
        assertFalse(absent.enabled(null)); assertEquals("false", absent.value(null))
        val settings = DebugSettings("helpful,foo=bar,baz=,longname", output::add)
        assertFailsWith<NullPointerException> { settings.enabled(null) }
        assertFailsWith<NullPointerException> { settings.value(null) }
        assertTrue(settings.enabled()); assertTrue(settings.enabled("name")); assertFalse(settings.enabled("FOO"))
        assertEquals("bar", settings.value("foo")); assertEquals("", settings.value("baz")); assertEquals("true", settings.value("name"))
        assertEquals("false", settings.value("absent"))
        assertEquals("\nICUDebug=helpful,foo=bar,baz=,longname", output.first())
        assertTrue(output.contains("\nICUDebug.enabled(name) = true"))
        assertTrue(output.contains("\nICUDebug.value(baz) = "))
        output.clear()
        val empty = DebugSettings("", output::add)
        assertTrue(empty.enabled()); assertTrue(empty.enabled("")); assertEquals("true", empty.value(""))
    }
}
