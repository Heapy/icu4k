// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class BidiExplicitCoreOracleTest {
    private val sourceClass = com.ibm.icu.text.Bidi::class.java
    private fun field(name: String) = sourceClass.getDeclaredField(name).apply { isAccessible = true }
    private fun method(name: String) = sourceClass.getDeclaredMethod(name).apply { isAccessible = true }
    private val properties = method("getDirProps")
    private val resolve = method("resolveExplicitLevels")
    private val check = method("checkExplicitLevels")
    private val direction = method("directionFromFlags")
    private fun outcome(action: () -> Any?): String = try { "ok:${action()}" } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        when (cause) {
            is IndexOutOfBoundsException -> "bounds"
            is IllegalArgumentException -> "argument:${cause.message}"
            else -> throw cause
        }
    }
    private fun prepare(text: String, level: Int, mode: Int, options: Int, order: Boolean): com.ibm.icu.text.Bidi? {
        val source = com.ibm.icu.text.Bidi()
        field("text").set(source, text.toCharArray())
        field("length").setInt(source, text.length)
        field("originalLength").setInt(source, text.length)
        field("paraCount").setInt(source, 1)
        field("paraLevel").setByte(source, level.toByte())
        field("defaultParaLevel").setByte(source, (if (level >= 126) level else 0).toByte())
        field("dirProps").set(source, ByteArray(text.length))
        field("levels").set(source, ByteArray(text.length))
        field("reorderingMode").setInt(source, mode)
        field("reorderingOptions").setInt(source, options)
        field("orderParagraphsLTR").setBoolean(source, order)
        try {
            properties.invoke(source)
        } catch (e: InvocationTargetException) {
            // ICU4J's inverse streaming preparation indexes paras_level[-1] when
            // no paragraph completed and the last strong direction is R.
            assertIs<ArrayIndexOutOfBoundsException>(e.targetException)
            assertEquals(4, options)
            assertTrue(level >= 126 && mode in 5..6)
            assertEquals(0, field("paraCount").getInt(source))
            e.targetException.stackTrace.firstOrNull()?.let {
                assertEquals("com.ibm.icu.text.Bidi", it.className)
                assertEquals("getDirProps", it.methodName)
            }
            return null
        }
        return source
    }
    private inner class Input(source: com.ibm.icu.text.Bidi) : BidiExplicitCore.Input {
        override val text = (field("text").get(source) as CharArray).copyOf()
        override val dirProps = (field("dirProps").get(source) as ByteArray).copyOf()
        override val levels = (field("levels").get(source) as ByteArray).copyOf()
        override val length = field("length").getInt(source)
        override val paraCount = field("paraCount").getInt(source)
        override val paraLimits = (field("paras_limit").get(source) as IntArray).copyOf()
        override val paraLevels = (field("paras_level").get(source) as ByteArray).copyOf()
        override val paraLevel = field("paraLevel").getByte(source)
        override val defaultParaLevel = field("defaultParaLevel").getByte(source)
        override val orderParagraphsLTR = field("orderParagraphsLTR").getBoolean(source)
        override val reorderingMode = field("reorderingMode").getInt(source)
        override var flags = field("flags").getInt(source)
        override var isolateCount = field("isolateCount").getInt(source)
        override fun getParaLevelAt(index: Int): Byte {
            if (defaultParaLevel.toInt() == 0 || index < paraLimits[0]) return paraLevel
            var i = 1
            while (i < paraCount) { if (index < paraLimits[i]) break; ++i }
            if (i >= paraCount) i = paraCount - 1
            return paraLevels[i]
        }
    }
    private fun compare(prepared: com.ibm.icu.text.Bidi?, validate: Boolean, label: String) {
        val source = prepared ?: return // Source preparation failed before this kernel.
        val input = Input(source)
        val port = BidiExplicitCore(input)
        assertEquals(outcome { (if (validate) check else resolve).invoke(source) }, outcome { if (validate) port.check() else port.resolve() }, label)
        assertContentEquals(field("dirProps").get(source) as ByteArray, input.dirProps, label)
        assertContentEquals(field("levels").get(source) as ByteArray, input.levels, label)
        assertEquals(field("flags").getInt(source), input.flags, label)
        assertEquals(field("isolateCount").getInt(source), input.isolateCount, label)
    }
    @Test fun explicitStackOverflowIsolatesBracketsAndParagraphsMatchSource() {
        val texts = listOf("abc", "אבג", "a(אב)z", "ا[12(א)]a", "a\r\nאב\na\u2029ب",
            "\u202aa\u202bא(12)\u202c\u202cz", "\u202da(אב)\u202cב", "a\u2066א\u2067b\u2069ג\u2069z",
            "a\u2068(אב)\u2069z", "\u2069a\u202cא", "𞤀a(𞤁)😀", "a\u200c\u0300א",
            "\u202b".repeat(140) + "a(אב)" + "\u202c".repeat(150),
            "\u2067".repeat(140) + "a(אב)" + "\u2069".repeat(150),
            "a\u202b\u2067".repeat(70) + "[א12]" + "\u202c\u2069".repeat(80),
            "a\nא\n".repeat(15))
        for (text in texts) for (mode in 0..6) for (options in listOf(0, 1, 2, 4)) for (level in listOf(0, 1, 2, 124, 125, 126, 127)) for (order in listOf(false, true)) {
            compare(prepare(text, level, mode, options, order), false, "mode=$mode options=$options level=$level order=$order")
        }
        val random = Random(0x5838)
        val alphabet = "abאבابت012٣٤()[]{}\u2329\u232a\u3008\u3009\u0300\u05b0 \t\r\n\u2029\u200c\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069"
        repeat(5000) {
            val text = buildString { repeat(random.nextInt(1, 200)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            compare(prepare(text, listOf(0, 1, 2, 124, 125, 126, 127).random(random), random.nextInt(7), listOf(0, 1, 2, 4).random(random), random.nextBoolean()), false, "random $it")
        }
    }
    @Test fun suppliedEmbeddingValidationPreservesErrorsAndPartialMutation() {
        val random = Random(0x5839)
        val text = "a\u2066(אב)\u2069\nا[12]\r\nb\u2067z\u2069"
        repeat(10000) { iteration ->
            val source = prepare(text, listOf(0, 1, 2, 124, 125, 126, 127).random(random), random.nextInt(7), 0, random.nextBoolean())!!
            val levels = ByteArray(if (iteration % 7 == 0) random.nextInt(text.length) else text.length) {
                if (iteration % 3 == 0) random.nextInt(256).toByte() else listOf(0, 1, 2, 3, -128, -127, -126).random(random).toByte()
            }
            field("levels").set(source, levels)
            compare(source, true, "validation $iteration")
        }
    }
    @Test fun arbitraryDirectionFlagsMatchSource() {
        val random = Random(0x583a)
        val source = prepare("a", 0, 0, 0, false)!!
        val input = Input(source)
        val port = BidiExplicitCore(input)
        repeat(10000) {
            val flags = random.nextInt()
            field("flags").setInt(source, flags); input.flags = flags
            assertEquals(direction.invoke(source), port.directionFromFlags())
        }
    }
}
