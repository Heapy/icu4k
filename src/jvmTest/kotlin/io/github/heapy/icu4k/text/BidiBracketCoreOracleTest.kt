// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class BidiBracketCoreOracleTest {
    private val sourceClass = com.ibm.icu.text.Bidi::class.java
    private val dataClass = Class.forName("com.ibm.icu.text.Bidi\$BracketData")
    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
    private fun method(name: String, vararg params: Class<*>) = sourceClass.getDeclaredMethod(name, dataClass, *params).apply { isAccessible = true }
    private val init = method("bracketInit")
    private val character = method("bracketProcessChar", Int::class.javaPrimitiveType!!)
    private val paragraph = method("bracketProcessB", Byte::class.javaPrimitiveType!!)
    private val boundary = method("bracketProcessBoundary", Int::class.javaPrimitiveType!!, Byte::class.javaPrimitiveType!!, Byte::class.javaPrimitiveType!!)
    private val lri = method("bracketProcessLRI_RLI", Byte::class.javaPrimitiveType!!)
    private val pdi = method("bracketProcessPDI")

    private class Input(override val text: CharArray, override val reorderingMode: Int, val level: Byte) : BidiBracketCore.Input {
        override val dirProps = ByteArray(text.size) { com.ibm.icu.lang.UCharacter.getDirection(text[it].code).toByte() }
        override val levels = ByteArray(text.size) { level }
        override fun getParaLevelAt(index: Int) = level
    }
    private inner class PairState(text: String, mode: Int, level: Byte) {
        val input = Input(text.toCharArray(), mode, level)
        val port = BidiBracketCore(input)
        val source = com.ibm.icu.text.Bidi()
        val data = dataClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val sourceProps = input.dirProps.copyOf()
        val sourceLevels = input.levels.copyOf()
        init {
            field(sourceClass, "text").set(source, input.text.copyOf())
            field(sourceClass, "dirProps").set(source, sourceProps)
            field(sourceClass, "levels").set(source, sourceLevels)
            field(sourceClass, "paraLevel").setByte(source, level)
            field(sourceClass, "reorderingMode").setInt(source, mode)
            init.invoke(source, data)
            port.init()
            check("init")
        }
        fun call(method: java.lang.reflect.Method, vararg args: Any, action: () -> Unit) {
            fun outcome(f: () -> Unit): String = try { f(); "ok" } catch (e: Throwable) {
                val cause = if (e is InvocationTargetException) e.targetException else e
                when (cause) {
                    is IndexOutOfBoundsException -> "bounds"
                    is NullPointerException -> "null"
                    else -> throw cause
                }
            }
            assertEquals(outcome { method.invoke(source, data, *args) }, outcome(action), method.name)
            check(method.name)
        }
        fun char(index: Int) = call(character, index) { port.processChar(index) }
        fun check(label: String) {
            assertContentEquals(sourceProps, input.dirProps, label)
            assertContentEquals(sourceLevels, input.levels, label)
            assertEquals(field(dataClass, "isoRunLast").get(data), port.isoRunLast, label)
            assertEquals(field(dataClass, "isNumbersSpecial").get(data), port.isNumbersSpecial, label)
            fun snapshot(array: Any?, names: List<String>): List<List<Any?>?> = (array as Array<*>).map { item ->
                item?.let { names.map { name -> field(it.javaClass, name).get(it) } }
            }
            val openingNames = listOf("position", "match", "contextPos", "flags", "contextDir")
            val runNames = listOf("contextPos", "start", "limit", "level", "lastStrong", "lastBase", "contextDir")
            assertEquals(snapshot(field(dataClass, "openings").get(data), openingNames),
                port.openings.map { it?.let { listOf(it.position, it.match, it.contextPos, it.flags, it.contextDir) } }, label)
            assertEquals(snapshot(field(dataClass, "isoRuns").get(data), runNames),
                port.isoRuns.map { it?.let { listOf(it.contextPos, it.start, it.limit, it.level, it.lastStrong, it.lastBase, it.contextDir) } }, label)
        }
    }
    @Test fun bracketAndNumberStateMatchesSourceAfterEveryCharacter() {
        val texts = listOf("", "abc", "אבג", "a(אב)z", "א(a)ב", "ا[12(א)]a", "a((ב))z", "א[(a)ב]c",
            "a\u2329ב\u3009", "א\u3008a\u232a", "[\u0300a]\u0300", "((א)(a))", "(אב[cd]12)",
            "(".repeat(100) + "אב12a" + ")".repeat(100), "a\u0000(ב)")
        for (mode in 0..6) for (level in listOf(0, 1, 2, 3, -128, -127, -126, -125)) for (text in texts) {
            val state = PairState(text, mode, level.toByte())
            for (i in text.indices) state.char(i)
        }
        val random = Random(0x4e30)
        val alphabet = "abאבابت012٣٤()[]{}<>\u2329\u232a\u3008\u3009\u0300\u05b0 \t\u200c\u2066\u2067\u2068\u2069"
        repeat(1000) {
            val text = buildString { repeat(random.nextInt(1, 100)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val state = PairState(text, random.nextInt(7), random.nextInt(4).toByte())
            for (i in text.indices) {
                if (random.nextInt(5) == 0) {
                    val level = (random.nextInt(4) or 0x80).toByte()
                    state.input.levels[i] = level; state.sourceLevels[i] = level
                }
                state.char(i)
            }
        }
    }
    @Test fun isolateBoundaryAndParagraphTransitionsRetainAllSourceState() {
        val random = Random(0x150)
        val text = "a([א12])ب {x}\u2066\u2067\u2068\u2069".repeat(10)
        repeat(100) {
            val state = PairState(text, random.nextInt(7), random.nextInt(4).toByte())
            var depth = 0
            for (i in text.indices) {
                when (random.nextInt(12)) {
                    0 -> {
                        val level = random.nextInt(126).toByte()
                        state.call(lri, level) { state.port.processLriRli(level) }; ++depth
                    }
                    1 -> if (depth > 0) { state.call(pdi) { state.port.processPdi() }; --depth }
                    2 -> {
                        val level = random.nextInt(126).toByte()
                        state.call(paragraph, level) { state.port.processB(level) }; depth = 0
                    }
                    3 -> {
                        val context = random.nextInt(256).toByte(); val embedding = random.nextInt(256).toByte()
                        state.call(boundary, i, context, embedding) { state.port.processBoundary(i, context, embedding) }
                    }
                    else -> state.char(i)
                }
            }
            state.call(init) { state.port.init() }
        }
    }
}
