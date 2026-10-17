// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.ArabicShaping as SourceShaping
import java.io.*
import java.lang.reflect.Modifier
import kotlin.random.Random
import kotlin.test.*

class ArabicShapingOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) {
        val name = e.javaClass.simpleName
        listOf(name, if (e is NullPointerException || e is IndexOutOfBoundsException) null else e.message)
    }
    private fun hex(text: String) = text.map { it.code.toString(16) }.joinToString(" ")
    private fun compare(text: String, options: Int) {
        val expected = outcome { SourceShaping(options).shape(text) }
        val actual = outcome { ArabicShaping(options).shape(text) }
        if (expected != actual) assertEquals(expected, actual, "options ${options.toString(16)} text ${hex(text)}")
    }
    private fun option(random: Random): Int =
        listOf(0, 1, 2, 3, 0x10000).random(random) or listOf(0, 4).random(random) or
        listOf(0, 8, 16, 24).random(random) or listOf(0, 32, 64, 96, 128).random(random) or
        listOf(0, 256).random(random) or listOf(0, 0x40000, 0x60000, 0x80000, 0xc0000).random(random) or
        listOf(0, 0x200000).random(random) or listOf(0, 0x1000000).random(random) or
        listOf(0, 0x4000000).random(random) or listOf(0, 0x8000000).random(random)

    @Test fun constantsTablesAndDescriptionsMatchSource() {
        val source = SourceShaping::class.java
        val port = ArabicShaping::class.java
        for (field in source.fields.filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType })
            assertEquals(field.getInt(null), port.getField(field.name).getInt(null), field.name)
        fun nested(value: Any?): Any? = when (value) {
            is IntArray -> value.toList()
            is CharArray -> value.toList()
            is Array<*> -> value.map(::nested)
            else -> value
        }
        for (name in listOf("irrelevantPos", "tailFamilyIsolatedFinal", "tashkeelMedial", "yehHamzaToYeh",
            "convertNormalizedLamAlef", "araLink", "presLink", "convertFEto06", "shapeTable")) {
            fun field(clazz: Class<*>) = clazz.getDeclaredField(name).apply { isAccessible = true }.get(null)
            assertEquals(nested(field(source)), nested(field(port)), name)
        }
        val random = Random(78_301)
        repeat(2_000) {
            val options = option(random)
            assertEquals(SourceShaping(options).toString().replace("com.ibm.icu.", "io.github.heapy.icu4k."), ArabicShaping(options).toString())
            assertEquals(SourceShaping(options).hashCode(), ArabicShaping(options).hashCode())
        }
    }

    @Test fun everyBmpCodeUnitWithJoiningAndDigitContexts() {
        val options = listOf(8, 12, 16, 20, 24, 28, 96, 128, 100, 132)
        for (code in 0..0xffff) {
            val ch = code.toChar()
            for (flags in options) compare("ب" + ch + "ل1", flags)
        }
    }

    @Test fun contextualLettersMarksSpacesAndOptionCombinations() {
        val texts = listOf("", "السلام عليكم", "لا لآ لأ لإ", "لالالا", " ﻻ ﻷ ﻹ ﻵ ", " شَّمْس سَلام ",
            "  س ش ص ض ئ  ", "سشصضئ", "ﺱ ﺵ ﺹ ﺽ ﺉ", "\u200bﺱ\ufe73ﺵ", "ءى ﺀﻯ", "ب\u200dل\u200cا",
            "1ا2۳a4", "ا12 A34 א56", "\u064b\u064c\u064d\u064e\u064f\u0650\u0651\u0652",
            "\ufe70\ufe73\ufe75\ufe7c\ufe7d\ufcf2\ufc5e", "\uffff\ufffe", "ب\u206d\u206e\u206fل", "😀لا𝟘")
        val random = Random(78_302)
        repeat(4_000) {
            val flags = option(random)
            for (text in texts) compare(text, flags)
        }
        val alphabet = (0x620..0x6d4).map(Int::toChar) + (0xfe70..0xfefd).map(Int::toChar) +
            "    0123456789אבAB\u200b\u200c\u200d\u206d\u206e\u206f\uffff\ufffe\ud83b\ude00".toList()
        repeat(15_000) {
            val text = CharArray(random.nextInt(1, 80)) { alphabet.random(random) }.concatToString()
            compare(text, option(random))
        }
    }

    @Test fun bufferSlicesSizeQueriesAliasingAndFailures() {
        val texts = listOf("", "لا", "ﻻ", "سَلام 12", " ﻻ ﻷ ﻹ ﻵ ", " س ئ ش ض ", "\uffff\ufffe", "Aا1ב2")
        val random = Random(78_303)
        repeat(1_000) {
            val options = option(random)
            val source = SourceShaping(options)
            val port = ArabicShaping(options)
            for (text in texts) {
                val input = ("##" + text + "!!").toCharArray()
                assertEquals(outcome { source.shape(input, 2, text.length, null, -123, 0) },
                    outcome { port.shape(input, 2, text.length, null, -123, 0) }, "sizing $options ${hex(text)}")
                for (capacity in listOf(1, text.length, text.length * 2 + 3).distinct()) {
                    val expected = CharArray(capacity + 5) { '#' }
                    val actual = expected.copyOf()
                    assertEquals(outcome { source.shape(input, 2, text.length, expected, 3, capacity) },
                        outcome { port.shape(input, 2, text.length, actual, 3, capacity) }, "buffer $options ${hex(text)}")
                    assertContentEquals(expected, actual, "buffer side effects $options")
                }
                for (destStart in listOf(0, 2, 3)) {
                    val expected = ("##" + text + "          ").toCharArray()
                    val actual = expected.copyOf()
                    assertEquals(outcome { source.shape(expected, 2, text.length, expected, destStart, expected.size - destStart) },
                        outcome { port.shape(actual, 2, text.length, actual, destStart, actual.size - destStart) })
                    assertContentEquals(expected, actual, "aliased buffers $options")
                }
                val expected = input.copyOf()
                val actual = input.copyOf()
                assertEquals(outcome { source.shape(expected, 2, text.length) }, outcome { port.shape(actual, 2, text.length) })
                assertContentEquals(expected, actual, "in-place $options")
            }
        }
    }

    @Test fun invalidOptionsAndArgumentValidationOrder() {
        for (options in listOf(0, 1, 8, 16, 0xa0, 0xe0, -1, 0x20000, 0xa0000, 0x10001, 0x40010)) {
            assertEquals(outcome { SourceShaping(options); "ok" }, outcome { ArabicShaping(options); "ok" })
            if ((options and 0xe0) > 0x80) continue
            val source = SourceShaping(options)
            val port = ArabicShaping(options)
            assertEquals(outcome { source.shape(null as String?) }, outcome { port.shape(null as String?) })
            assertEquals(outcome { source.shape(null, 0, 0) }, outcome { port.shape(null, 0, 0) })
            for (sourceStart in listOf(-1, 0, 2, 4)) for (length in listOf(-1, 0, 1, 3))
                for (destStart in listOf(-1, 0, 4)) for (capacity in listOf(-1, 0, 1, 3)) {
                    val input = "abc".toCharArray()
                    for (isNull in listOf(false, true)) {
                        val expected = if (isNull) null else CharArray(3) { '#' }
                        val actual = expected?.copyOf()
                        assertEquals(outcome { source.shape(input, sourceStart, length, expected, destStart, capacity) },
                            outcome { port.shape(input, sourceStart, length, actual, destStart, capacity) },
                            "validation $options $sourceStart $length $destStart $capacity $isNull")
                        assertContentEquals(expected, actual)
                    }
                }
            assertEquals(outcome { source.shape(null, -1, -1, null, -1, -1) }, outcome { port.shape(null, -1, -1, null, -1, -1) })
        }
    }

    @Test fun shapingExceptionDescriptorAndStreamsMatchSource() {
        val source = ObjectStreamClass.lookup(com.ibm.icu.text.ArabicShapingException::class.java)
        val port = ObjectStreamClass.lookup(ArabicShapingException::class.java)
        assertEquals(source.serialVersionUID, port.serialVersionUID)
        assertEquals(source.fields.toList(), port.fields.toList())
        for (value in listOf(com.ibm.icu.text.ArabicShapingException("no room"), ArabicShapingException("no room"))) {
            val buffer = ByteArrayOutputStream()
            ObjectOutputStream(buffer).use { it.writeObject(value) }
            val from = if (value is ArabicShapingException) "io.github.heapy.icu4k." else "com.ibm.icu."
            val to = if (value is ArabicShapingException) "com.ibm.icu." else "io.github.heapy.icu4k."
            val restored = object : ObjectInputStream(ByteArrayInputStream(buffer.toByteArray())) {
                override fun resolveClass(desc: ObjectStreamClass): Class<*> =
                    if (desc.name.startsWith(from)) Class.forName(desc.name.replace(from, to)) else super.resolveClass(desc)
            }.use { it.readObject() as Exception }
            assertEquals(to + "text.ArabicShapingException", restored.javaClass.name)
            assertEquals("no room", restored.message)
        }
    }
}
