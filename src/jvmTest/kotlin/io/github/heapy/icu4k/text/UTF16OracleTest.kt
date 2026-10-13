package io.github.heapy.icu4k.text

import com.ibm.icu.text.UTF16 as JavaUTF16
import kotlin.test.*

class UTF16OracleTest {
    // Exhaustive short strings exercise every combination of paired/unpaired surrogates.
    private fun texts(): List<String> {
        val all = mutableListOf("")
        var level = listOf("")
        repeat(4) {
            level = level.flatMap { prefix -> listOf('a', '\ud800', '\udbff', '\udc00', '\udfff').map { prefix + it } }
            all.addAll(level)
        }
        return all
    }

    private fun sameResult(expected: () -> Int, actual: () -> Int) {
        val result = try { expected() } catch (_: IndexOutOfBoundsException) {
            assertFailsWith<IndexOutOfBoundsException> { actual() }
            return
        }
        assertEquals(result, actual())
    }

    @Test fun exhaustiveAccessAndOffsets() {
        for (text in texts()) {
            assertEquals(JavaUTF16.countCodePoint(text), UTF16.countCodePoint(text))
            assertEquals(JavaUTF16.getSingleCodePoint(text), UTF16.getSingleCodePoint(text))
            assertEquals(JavaUTF16.reverse(StringBuffer(text)).toString(), UTF16.reverse(text).toString())
            for (offset in text.indices) {
                assertEquals(JavaUTF16.charAt(text, offset), UTF16.charAt(text, offset))
                assertEquals(JavaUTF16.bounds(text, offset), UTF16.bounds(text, offset))
                assertEquals(JavaUTF16.valueOf(text, offset), UTF16.valueOf(text, offset))
            }
            for (offset in -1..text.length + 1) {
                sameResult({ JavaUTF16.findCodePointOffset(text, offset) }, { UTF16.findCodePointOffset(text, offset) })
                sameResult({ JavaUTF16.findOffsetFromCodePoint(text, offset) }, { UTF16.findOffsetFromCodePoint(text, offset) })
                for (shift in -5..5) {
                    sameResult({ JavaUTF16.moveCodePointOffset(text, offset, shift) }, { UTF16.moveCodePointOffset(text, offset, shift) })
                }
            }
            for (number in -1..5) assertEquals(JavaUTF16.hasMoreCodePointsThan(text, number), UTF16.hasMoreCodePointsThan(text, number))
            val padded = ("\ud800" + text + "\udc00").toCharArray()
            for (offset in text.indices) {
                assertEquals(JavaUTF16.charAt(padded, 1, padded.size - 1, offset), UTF16.charAt(padded, 1, padded.size - 1, offset))
                assertEquals(JavaUTF16.bounds(padded, 1, padded.size - 1, offset), UTF16.bounds(padded, 1, padded.size - 1, offset))
            }
        }
    }

    @Test fun codePointSearchAndReplacementMatchIcu() {
        val codePoints = listOf(0, 0x61, 0xd800, 0xdbff, 0xdc00, 0xdfff, 0x10000, 0x10ffff)
        for (text in texts()) for (cp in codePoints) {
            assertEquals(JavaUTF16.indexOf(text, cp), UTF16.indexOf(text, cp))
            assertEquals(JavaUTF16.lastIndexOf(text, cp), UTF16.lastIndexOf(text, cp))
            for (from in -1..text.length + 1) {
                assertEquals(JavaUTF16.indexOf(text, cp, from), UTF16.indexOf(text, cp, from))
                assertEquals(JavaUTF16.lastIndexOf(text, cp, from), UTF16.lastIndexOf(text, cp, from))
            }
            for (replacement in codePoints) {
                if (cp == 0 || replacement == 0) {
                    assertFailsWith<IllegalArgumentException> { JavaUTF16.replace(text, cp, replacement) }
                    assertFailsWith<IllegalArgumentException> { UTF16.replace(text, cp, replacement) }
                } else {
                    assertEquals(JavaUTF16.replace(text, cp, replacement), UTF16.replace(text, cp, replacement))
                }
            }
        }
    }

    @Test fun everyUnicodeCodePoint() {
        for (cp in 0..0x10ffff) {
            assertEquals(JavaUTF16.valueOf(cp), UTF16.valueOf(cp))
            assertEquals(JavaUTF16.getLeadSurrogate(cp), UTF16.getLeadSurrogate(cp))
            assertEquals(JavaUTF16.getTrailSurrogate(cp), UTF16.getTrailSurrogate(cp))
            assertEquals(JavaUTF16.getCharCount(cp), UTF16.getCharCount(cp))
            assertEquals(JavaUTF16.isSurrogate(cp), UTF16.isSurrogate(cp))
        }
    }

    @Test fun mutableOperationsMatchUpstream() {
        val codePoints = listOf(0, 0x61, 0xd800, 0xdc00, 0x10000, 0x10ffff)
        for (text in texts()) {
            for (offset in text.indices) {
                val javaDelete = StringBuffer(text)
                val kotlinDelete = StringBuilder(text)
                JavaUTF16.delete(javaDelete, offset)
                UTF16.delete(kotlinDelete, offset)
                assertEquals(javaDelete.toString(), kotlinDelete.toString())
                for (cp in codePoints) {
                    val javaSet = StringBuffer(text)
                    val kotlinSet = StringBuilder(text)
                    JavaUTF16.setCharAt(javaSet, offset, cp)
                    UTF16.setCharAt(kotlinSet, offset, cp)
                    assertEquals(javaSet.toString(), kotlinSet.toString())
                    val javaArray = CharArray(text.length + 4)
                    text.toCharArray().copyInto(javaArray)
                    val kotlinArray = javaArray.copyOf()
                    val a = JavaUTF16.setCharAt(javaArray, text.length, offset, cp)
                    val b = UTF16.setCharAt(kotlinArray, text.length, offset, cp)
                    assertEquals(a, b)
                    assertContentEquals(javaArray, kotlinArray)
                }
            }
            for (offset in 0..text.length) for (cp in codePoints) {
                val javaInsert = StringBuffer(text)
                val kotlinInsert = StringBuilder(text)
                JavaUTF16.insert(javaInsert, offset, cp)
                UTF16.insert(kotlinInsert, offset, cp)
                assertEquals(javaInsert.toString(), kotlinInsert.toString())
                val javaArray = CharArray(text.length + 4)
                text.toCharArray().copyInto(javaArray)
                val kotlinArray = javaArray.copyOf()
                assertEquals(JavaUTF16.insert(javaArray, text.length, offset, cp), UTF16.insert(kotlinArray, text.length, offset, cp))
                assertContentEquals(javaArray, kotlinArray)
            }
        }
    }
}
