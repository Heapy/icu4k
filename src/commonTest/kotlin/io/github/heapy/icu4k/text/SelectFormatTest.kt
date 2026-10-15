// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class SelectFormatTest {
    @Test fun selectionNestedArgumentsAndFallback() {
        val f = SelectFormat("female{She has {n,plural,one{# file}other{# files}}} other{They have files}")
        assertEquals("She has {n,plural,one{# file}other{# files}}", f.format("female"))
        assertEquals("They have files", f.format("unknown"))
        val out = StringBuilder("prefix:")
        val pos = FormatFieldPosition(1).also { it.setBeginIndex(5); it.setEndIndex(8) }
        assertSame(out, f.format("female" as Any, out, pos))
        assertEquals(5, pos.getBeginIndex())
        assertEquals(8, pos.getEndIndex())
        assertFailsWith<IllegalArgumentException> { f.format("bad keyword") }
        assertFailsWith<UnsupportedOperationException> { f.parseObject(null, null as ParsePosition?) }
    }
    @Test fun failureResetsAndCloneSharesParsedPattern() {
        val f = SelectFormat("other{old}")
        val clone = f.clone() as SelectFormat
        clone.applyPattern("other{new}")
        assertEquals("new", f.format("x"))
        assertEquals("other{old}", f.toPattern())
        assertEquals(f, clone)
        assertFailsWith<IllegalArgumentException> { clone.applyPattern("one{missing fallback}") }
        assertFailsWith<IllegalStateException> { f.format("other") }
        assertNull(clone.toPattern())
        clone.applyPattern("a{first} a{second} other{fallback}")
        assertEquals("first", clone.format("a"))
    }
}
