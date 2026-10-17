// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class BidiPropertyCoreOracleTest {
    private val clazz = com.ibm.icu.text.Bidi::class.java
    private fun field(name: String) = clazz.getDeclaredField(name).apply { isAccessible = true }
    private val scan = clazz.getDeclaredMethod("getDirProps").apply { isAccessible = true }
    private val allocateLevels = clazz.getDeclaredMethod("getLevelsMemory", Int::class.javaPrimitiveType).apply { isAccessible = true }
    private val explicit = clazz.getDeclaredMethod("resolveExplicitLevels").apply { isAccessible = true }
    private val paraLevelAt = clazz.getDeclaredMethod("GetParaLevelAt", Int::class.javaPrimitiveType).apply { isAccessible = true }
    private fun outcome(action: () -> Any?): String = try { "ok:${action()}" } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        when (cause) {
            is IndexOutOfBoundsException -> "bounds"
            is NullPointerException -> "null"
            is IllegalStateException -> "state:${cause.message}"
            else -> throw cause
        }
    }
    private fun compare(text: String, level: Int, mode: Int, options: Int, order: Boolean, prologue: String?, custom: ((Int) -> Int)? = null, failAfter: Int = -1, compose: Boolean = true, seeded: Boolean = false) {
        val sourceCalls = mutableListOf<Int>()
        val portCalls = mutableListOf<Int>()
        val source = object : com.ibm.icu.text.Bidi() {
            override fun getCustomizedClass(c: Int): Int {
                sourceCalls += c
                if (sourceCalls.size == failAfter) throw IllegalStateException("classifier failure")
                return custom?.invoke(c) ?: super.getCustomizedClass(c)
            }
        }
        val port = BidiPropertyFixture(text, level, mode, options, order, prologue) { c ->
            portCalls += c
            if (portCalls.size == failAfter) throw IllegalStateException("classifier failure")
            custom?.invoke(c) ?: BidiPropertyCore.customizedClass(c, null)
        }
        field("text").set(source, text.toCharArray())
        field("originalLength").setInt(source, text.length)
        field("length").setInt(source, text.length)
        field("paraCount").setInt(source, 1)
        field("paraLevel").setByte(source, level.toByte())
        field("defaultParaLevel").setByte(source, port.defaultParaLevel)
        field("dirProps").set(source, ByteArray(text.length))
        field("levels").set(source, ByteArray(text.length))
        field("reorderingMode").setInt(source, mode)
        field("reorderingOptions").setInt(source, options)
        field("orderParagraphsLTR").setBoolean(source, order)
        field("prologue").set(source, prologue)
        if (seeded) {
            port.controlCount = 77; field("controlCount").setInt(source, 77)
            port.isolateCount = 11; field("isolateCount").setInt(source, 11)
            port.lastArabicPos = 88; field("lastArabicPos").setInt(source, 88)
            port.flags = -1; field("flags").setInt(source, -1)
            port.dirProps.fill(-1); (field("dirProps").get(source) as ByteArray).fill(-1)
            port.levels.fill(29); (field("levels").get(source) as ByteArray).fill(29)
            port.paraLimits = IntArray(5) { 100 + it }
            port.paraLevels = ByteArray(5) { 30 }
            field("paras_limit").set(source, port.paraLimits.copyOf())
            field("paras_level").set(source, port.paraLevels.copyOf())
        }
        field("levelsMemory").set(source, field("levels").get(source))
        fun checkState() {
            assertEquals(sourceCalls, portCalls, "classifier calls")
            assertContentEquals(field("text").get(source) as CharArray, port.text)
            assertContentEquals(field("dirProps").get(source) as ByteArray, port.dirProps)
            assertContentEquals(field("levels").get(source) as ByteArray, port.levels)
            assertContentEquals(field("paras_limit").get(source) as IntArray, port.paraLimits)
            assertContentEquals(field("paras_level").get(source) as ByteArray, port.paraLevels)
            for ((name, value) in listOf("length" to port.length, "paraCount" to port.paraCount, "flags" to port.flags,
                "lastArabicPos" to port.lastArabicPos, "controlCount" to port.controlCount, "isolateCount" to port.isolateCount)) {
                assertEquals(field(name).getInt(source), value, name)
            }
            assertEquals(field("paraLevel").getByte(source), port.paraLevel)
        }
        val result = outcome { scan.invoke(source); Unit }
        assertEquals(result, outcome { BidiPropertyCore(port).scan(); Unit }, "level=$level mode=$mode options=$options text=$text")
        checkState()
        for (i in listOf(-1, 0, 1, text.length / 2, text.length - 1, text.length, text.length + 1, Int.MAX_VALUE)) {
            assertEquals(outcome { paraLevelAt.invoke(source, i) }, outcome { port.getParaLevelAt(i) }, "paragraph index $i")
        }
        if (result == "ok:kotlin.Unit" && compose) {
            val oldSourceLevels = field("levels").get(source)
            val oldPortLevels = port.levels
            allocateLevels.invoke(source, port.length)
            field("levels").set(source, field("levelsMemory").get(source))
            if (port.levels.size != port.length) port.levels = ByteArray(port.length)
            assertEquals(oldSourceLevels === field("levels").get(source), oldPortLevels === port.levels, "level buffer reuse")
            assertEquals(outcome { explicit.invoke(source) }, outcome { BidiExplicitCore(port).resolve() }, "property/explicit composition")
            checkState()
        }
    }
    @Test fun paragraphDefaultLevelsStreamingAndIsolateOverflowMatchSource() {
        val texts = listOf("a", "אב", "ابت", "12(אב) a", "😀𞤀𞸀\ud800\udc00\ud800X\udc00", "a\r\nאב\na\u2029ب",
            "\u2068(אב)\u2069a", "\u2068(abc)\u2069א", "\u2068\u2066א\u2069a\u2069",
            "\u2069\u2068\u2067א\u2069\u2069", "\u2068".repeat(140) + "א" + "\u2069".repeat(145),
            "\u2068".repeat(140) + "a", "a\nא\nب\r\n".repeat(30), "a\u200c\u200d\u200e\u200f\u202a\u202c\u2066\u2069א\n")
        for (text in texts) for (level in listOf(0, 1, 2, 125, 126, 127)) for (mode in 0..6) for (options in listOf(0, 1, 2, 3, 4, 6)) {
            compare(text, level, mode, options, mode % 2 == 0, null)
        }
        for (prologue in listOf(null, "", "a", "א", "ابت", "12", "א\na", "a\nא", "א\n12", "😀𞤀", "\ud800א\udc00")) {
            for (level in listOf(126, 127)) for (mode in 0..6) {
                compare("12(אב)\na", level, mode, 0, true, prologue)
            }
        }
    }
    @Test fun everyUnicodeValueMatchesSourcePropertyScanning() {
        // A separator prevents adjacent isolated surrogate values from becoming
        // an unintended pair; supplementary values still use their actual pair.
        for (plane in 0..16) {
            val text = buildString {
                for (c in (plane shl 16) until ((plane + 1) shl 16)) {
                    append(String(Character.toChars(c)))
                    append('a')
                }
            }
            compare(text, 126, 0, 2, false, "12", compose = false)
        }
    }
    @Test fun randomParagraphScanningAndCompositionMatchSource() {
        val random = Random(0x5033)
        val alphabet = "abאבابت012٣٤()[]{}\u0300\u05b0 \t\r\n\u2029\u200c\u200d\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069\ud800\udc00"
        repeat(5000) {
            fun text(size: Int) = buildString { repeat(size) { append(alphabet[random.nextInt(alphabet.length)]) } }
            compare(text(random.nextInt(1, 200)), listOf(0, 1, 2, 125, 126, 127).random(random), random.nextInt(7), random.nextInt(8), random.nextBoolean(),
                if (random.nextBoolean()) null else text(random.nextInt(30)))
        }
    }
    @Test fun customDirectionCallbacksAndFailuresPreserveSourceMutationOrder() {
        val text = "x(𞤀)\u2068x\u2069\na\u200cא"
        for (level in listOf(0, 1, 126, 127)) for (mode in 0..6) for (options in listOf(0, 2, 4, 6)) {
            for (direction in listOf(-129, -128, -1, 0, 1, 7, 10, 13, 19, 20, 21, 22, 23, 24, 255, 256)) {
                compare(text, level, mode, options, false, "x\nא", { c -> if (c == 'x'.code) direction else com.ibm.icu.lang.UCharacter.getDirection(c) })
            }
            for (failAfter in 1..8) compare(text, level, mode, options, true, "א\na", failAfter = failAfter, compose = false)
        }
    }
    @Test fun reusedStorageAndCountersRetainSourceContentsAndPartialWrites() {
        for (text in listOf("abc", "אב", "a\nא\nب\r\n".repeat(15), "12\u2068א\u2069")) {
            for (level in listOf(0, 1, 125, 126, 127)) for (mode in listOf(0, 1, 5, 6)) for (options in listOf(0, 2, 4, 6)) {
                for (failAfter in listOf(-1, 1, 4)) {
                    compare(text, level, mode, options, true, "א\na", failAfter = failAfter, seeded = true)
                }
            }
        }
    }
    @Test fun classifierSentinelClampingAndNegativeResultsMatchSource() {
        val source = com.ibm.icu.text.Bidi()
        for (result in listOf(-257, -129, -128, -1, 0, 1, 13, 22, 23, 24, 255, 256, Int.MAX_VALUE)) {
            source.customClassifier = object : com.ibm.icu.text.BidiClassifier(null) { override fun classify(c: Int) = result }
            val port = object : BidiClassifier(null) { override fun classify(c: Int) = result }
            for (c in listOf(-1, 0, 65, 0x5d0, 0x627, 0x1e900, 0x10ffff, 0x110000, Int.MAX_VALUE)) {
                assertEquals(source.getCustomizedClass(c), BidiPropertyCore.customizedClass(c, port))
            }
        }
    }
    @Test fun callerArrayMutationKeepsSourceBackwardPairingAndExactJvmFailure() {
        val sourceText = "a\udc00".toCharArray()
        val sourceCalls = mutableListOf<Int>()
        val portCalls = mutableListOf<Int>()
        val source = object : com.ibm.icu.text.Bidi() {
            override fun getCustomizedClass(c: Int): Int {
                sourceCalls += c
                if (sourceCalls.size == 1) sourceText[0] = '\ud800'
                return super.getCustomizedClass(c)
            }
        }
        lateinit var port: BidiPropertyFixture
        port = BidiPropertyFixture("a\udc00", 126, 0, 0, false) {
            portCalls += it
            if (portCalls.size == 1) port.text[0] = '\ud800'
            BidiPropertyCore.customizedClass(it, null)
        }
        field("text").set(source, sourceText)
        field("originalLength").setInt(source, 2)
        field("length").setInt(source, 2)
        field("paraCount").setInt(source, 1)
        field("paraLevel").setByte(source, 126)
        field("defaultParaLevel").setByte(source, 126)
        field("dirProps").set(source, ByteArray(2))
        field("levels").set(source, ByteArray(2))
        val sourceError = assertFailsWith<InvocationTargetException> { scan.invoke(source) }.targetException
        val portError = assertFailsWith<IndexOutOfBoundsException> { BidiPropertyCore(port).scan() }
        assertEquals(sourceError.javaClass.name, portError.javaClass.name)
        assertEquals(sourceCalls, portCalls)
        assertEquals(listOf('a'.code, 0x10000), sourceCalls)
        assertContentEquals(sourceText, port.text)
        assertContentEquals(field("dirProps").get(source) as ByteArray, port.dirProps)
        assertEquals(field("paraLevel").getByte(source), port.paraLevel)
        assertEquals(field("flags").getInt(source), port.flags)
    }
    @Test fun prologueReplacementInsideClassifierUsesCurrentSourceContext() {
        for (replacement in listOf("b", "ג\nb", null)) {
            val sourceCalls = mutableListOf<Int>()
            val portCalls = mutableListOf<Int>()
            val source = object : com.ibm.icu.text.Bidi() {
                override fun getCustomizedClass(c: Int): Int {
                    sourceCalls += c
                    if (sourceCalls.size == 1) setContext(replacement, null)
                    return super.getCustomizedClass(c)
                }
            }
            lateinit var port: BidiPropertyFixture
            port = BidiPropertyFixture("12", 126, 0, 0, false, "אלף\nabc") {
                portCalls += it
                if (portCalls.size == 1) port.prologue = replacement
                BidiPropertyCore.customizedClass(it, null)
            }
            source.setContext("אלף\nabc", null)
            field("text").set(source, "12".toCharArray())
            field("originalLength").setInt(source, 2)
            field("length").setInt(source, 2)
            field("paraCount").setInt(source, 1)
            field("paraLevel").setByte(source, 126)
            field("defaultParaLevel").setByte(source, 126)
            field("dirProps").set(source, ByteArray(2))
            field("levels").set(source, ByteArray(2))
            assertEquals(outcome { scan.invoke(source); Unit }, outcome { BidiPropertyCore(port).scan(); Unit })
            assertEquals(sourceCalls, portCalls)
            assertContentEquals(field("dirProps").get(source) as ByteArray, port.dirProps)
            assertContentEquals(field("paras_level").get(source) as ByteArray, port.paraLevels)
            assertEquals(field("paraLevel").getByte(source), port.paraLevel)
            assertEquals(field("flags").getInt(source), port.flags)
        }
    }
}
