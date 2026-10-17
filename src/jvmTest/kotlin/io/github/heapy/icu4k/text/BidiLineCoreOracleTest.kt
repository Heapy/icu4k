// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import java.lang.reflect.InvocationTargetException
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.*

class BidiLineCoreOracleTest {
    private val clazz = com.ibm.icu.text.Bidi::class.java
    private val lineClass = Class.forName("com.ibm.icu.text.BidiLine")
    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
    private fun field(name: String) = field(clazz, name)
    private fun method(name: String, vararg params: Class<*>) = lineClass.getDeclaredMethod(name, clazz, *params).apply { isAccessible = true }
    private val getRuns = method("getRuns")
    private val getLevels = method("getLevels")
    private val trailing = method("setTrailingWSStart")
    private val getLogicalMap = method("getLogicalMap")
    private val getVisualMap = method("getVisualMap")
    private val getLogicalRun = method("getLogicalRun", Int::class.javaPrimitiveType!!)
    private val getVisualRun = method("getVisualRun", Int::class.javaPrimitiveType!!)
    private val getLogicalIndex = method("getLogicalIndex", Int::class.javaPrimitiveType!!)
    private val getVisualIndex = method("getVisualIndex", Int::class.javaPrimitiveType!!)
    private val getLevelAt = method("getLevelAt", Int::class.javaPrimitiveType!!)
    private val setLine = method("setLine", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    private fun sourceRun(r: Any?): Any? = r?.let {
        val t = it.javaClass
        listOf(field(t, "start").getInt(it), field(t, "limit").getInt(it), field(t, "level").getByte(it), field(t, "insertRemove").getInt(it))
    }
    private fun portRun(r: BidiRun?): Any? = r?.let { listOf(it.start, it.limit, it.level, it.insertRemove) }
    private fun result(action: () -> Any?): Pair<String, Any?> = try { "ok" to action() } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        when (cause) {
            is IndexOutOfBoundsException -> "bounds" to null
            is NullPointerException -> "null" to null
            is IllegalStateException -> "state:${cause.message}" to null
            is IllegalArgumentException -> "argument:${cause.message}" to null
            is OutOfMemoryError -> "memory:${cause.message}" to null
            else -> throw cause
        }
    }
    private fun copy(source: com.ibm.icu.text.Bidi): BidiLineFixture {
        val p = BidiLineFixture()
        p.text = (field("text").get(source) as CharArray).copyOf()
        p.originalLength = field("originalLength").getInt(source); p.length = field("length").getInt(source)
        p.resultLength = field("resultLength").getInt(source); p.runCount = field("runCount").getInt(source)
        p.paraLevel = field("paraLevel").getByte(source); p.defaultParaLevel = field("defaultParaLevel").getByte(source)
        p.direction = field("direction").getByte(source); p.trailingWSStart = field("trailingWSStart").getInt(source)
        p.paraCount = field("paraCount").getInt(source); p.paraLimits = (field("paras_limit").get(source) as IntArray).copyOf(); p.paraLevels = (field("paras_level").get(source) as ByteArray).copyOf()
        p.reorderingMode = source.reorderingMode; p.reorderingOptions = source.reorderingOptions
        p.controlCount = field("controlCount").getInt(source); p.flags = field("flags").getInt(source)
        p.orderParagraphsLTR = source.isOrderParagraphsLTR; p.inverse = source.isInverse
        val bytes = IdentityHashMap<Any, ByteArray>()
        fun bytes(name: String): ByteArray { val original = field(name).get(source) as ByteArray; return bytes.getOrPut(original) { original.copyOf() } }
        p.dirProps = bytes("dirProps"); p.levels = bytes("levels"); p.dirPropsMemory = bytes("dirPropsMemory"); p.levelsMemory = bytes("levelsMemory")
        val arrays = IdentityHashMap<Any, Array<BidiRun?>>()
        val objects = IdentityHashMap<Any, BidiRun>()
        fun runs(name: String): Array<BidiRun?>? {
            val original = field(name).get(source) as Array<*>? ?: return null
            return arrays.getOrPut(original) { original.map { r -> r?.let {
                objects.getOrPut(it) {
                    val values = sourceRun(it) as List<*>
                    BidiRun(values[0] as Int, values[1] as Int, values[2] as Byte).also { run -> run.insertRemove = values[3] as Int }
                }
            } }.toTypedArray() }
        }
        p.runs = runs("runs"); p.simpleRuns = runs("simpleRuns")!!; p.runsMemory = runs("runsMemory")!!
        if (!field("mayAllocateRuns").getBoolean(source)) p.allocateRuns = { needed, old ->
            if (needed <= old.size) old else throw OutOfMemoryError("Failed to allocate memory for Runs")
        }
        val points = field("insertPoints").get(source)
        p.insertPoints.size = field(points.javaClass, "size").getInt(points); p.insertPoints.confirmed = field(points.javaClass, "confirmed").getInt(points)
        p.insertPoints.points = (field(points.javaClass, "points").get(points) as Array<*>).map { it?.let { BidiImplicitCore.Point(field(it.javaClass, "pos").getInt(it), field(it.javaClass, "flag").getInt(it)) } }.toTypedArray()
        return p
    }
    private fun checkState(source: com.ibm.icu.text.Bidi, p: BidiLineFixture) {
        for ((name, value) in listOf("length" to p.length, "originalLength" to p.originalLength, "resultLength" to p.resultLength,
            "runCount" to p.runCount, "trailingWSStart" to p.trailingWSStart, "controlCount" to p.controlCount, "paraCount" to p.paraCount)) assertEquals(field(name).getInt(source), value, name)
        assertEquals(field("paraLevel").getByte(source), p.paraLevel)
        assertEquals(field("direction").getByte(source), p.direction)
        assertContentEquals(field("text").get(source) as CharArray, p.text)
        assertContentEquals(field("dirProps").get(source) as ByteArray, p.dirProps)
        assertContentEquals(field("levels").get(source) as ByteArray, p.levels)
        for ((name, array) in listOf("runs" to p.runs, "runsMemory" to p.runsMemory, "simpleRuns" to p.simpleRuns)) {
            val original = field(name).get(source) as Array<*>?
            assertEquals(original?.map(::sourceRun), array?.map(::portRun), name)
        }
        assertEquals(field("runs").get(source) === field("runsMemory").get(source), p.runs === p.runsMemory)
        assertEquals(field("runs").get(source) === field("simpleRuns").get(source), p.runs === p.simpleRuns)
    }
    private fun compare(source: com.ibm.icu.text.Bidi, p: BidiLineFixture) {
        val core = BidiLineCore(p)
        fun call(method: java.lang.reflect.Method, vararg args: Any, action: () -> Any?): Boolean {
            val s = result { method.invoke(null, source, *args) }
            val a = result(action)
            assertEquals(s.first, a.first, method.name)
            if (s.first == "ok") when (val value = s.second) {
                is ByteArray -> { assertContentEquals(value, a.second as ByteArray); assertEquals(value === field("levels").get(source), a.second === p.levels) }
                is IntArray -> assertContentEquals(value, a.second as IntArray)
                is com.ibm.icu.text.BidiRun -> assertEquals(sourceRun(value), portRun(a.second as BidiRun))
                else -> if (value != null) assertEquals(value, a.second)
            }
            checkState(source, p)
            return s.first == "ok"
        }
        checkState(source, p)
        if (!call(getRuns) { core.getRuns(); null }) return
        for (r in 0 until p.runCount) call(getVisualRun, r) { core.getVisualRun(r) }
        for (i in 0 until p.length) {
            call(getLevelAt, i) { core.getLevelAt(i) }
            call(getLogicalRun, i) { core.getLogicalRun(i) }
            call(getVisualIndex, i) { core.getVisualIndex(i) }
        }
        for (i in 0 until p.resultLength) call(getLogicalIndex, i) { core.getLogicalIndex(i) }
        call(getLogicalMap) { core.getLogicalMap() }; call(getVisualMap) { core.getVisualMap() }
        call(getLevels) { core.getLevels() }; call(getLevels) { core.getLevels() }
        for (options in listOf(0, 1, 2, 3, 4, 8, 10, 16, 18, 20, 31)) {
            val s = result { source.writeReordered(options) }; val a = result { BidiWriterCore.writeReordered(p.writerInput(), options) }
            assertEquals(s, a, "writer options $options")
            checkState(source, p)
        }
    }
    private fun prepare(text: String, level: Int, mode: Int, options: Int, maxLength: Int = 0, maxRuns: Int = 0): com.ibm.icu.text.Bidi? {
        val source = com.ibm.icu.text.Bidi(maxLength, maxRuns)
        source.setReorderingMode(mode); source.setReorderingOptions(options)
        val r = result { source.setPara(text, level.toByte(), null) }
        if (r.first != "ok") {
            assertTrue(r.first == "bounds" || r.first == "null" || (mode == 3 && maxRuns > 0 && r.first == "memory:Failed to allocate memory for Runs"), r.first)
            return null // Source resolver produced no valid paragraph.
        }
        return source
    }
    @Test fun paragraphAndLineRunsMapsLevelsAndWriterMatchSource() {
        val texts = listOf("", "a", "אבג", "a(אב)12 ب٣ z", "a\r\nאב\na\u2029ب", "a\u2067אב \u2066x\u2069z\u2069 q",
            "\u202da(אב)\u202cب", "😀𞤀𞸀\ud800X\udc00", "א a   \n", "a\u200c\u200d\u200e\u200fא\u202c ",
            "ا 12 a 34 אב 56 a .".repeat(4), "aא".repeat(15))
        for (text in texts) for (level in listOf(0, 1, 125, 126, 127)) for (mode in 0..6) for (options in listOf(0, 1, 2, 4)) {
            val source = prepare(text, level, mode, options) ?: continue
            val p = copy(source)
            compare(source, p)
            if (source.processedLength > 0) {
                // A new source instance preserves line behavior before getLevels caching.
                val parent = prepare(text, level, mode, options)!!
                val pp = copy(parent)
                val ranges = listOf(0 to parent.processedLength, 0 to 1, parent.processedLength - 1 to parent.processedLength)
                for ((start, limit) in ranges) {
                    val expected = result { setLine.invoke(null, parent, start, limit) }
                    val line = BidiLineFixture()
                    val actual = result { BidiLineCore(pp).setLine(start, limit, line) }
                    assertEquals(expected.first, actual.first, "line $start..$limit mode=$mode options=$options")
                    checkState(parent, pp)
                    if (expected.first == "ok") {
                        assertSame(pp, line.parent)
                        assertFalse(line.inverse)
                        compare(expected.second as com.ibm.icu.text.Bidi, line)
                    } else assertNull(line.parent)
                }
            }
        }
    }
    @Test fun randomizedValidLinesAndFixedRunCapacityMatchSource() {
        val random = Random(0x4c31)
        val alphabet = "abאבابت012٣٤()[] \t\r\n\u2029\u200c\u200d\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069\ud800\udc00"
        repeat(1000) {
            val text = buildString { repeat(random.nextInt(1, 65)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val source = prepare(text, listOf(0, 1, 125, 126, 127).random(random), random.nextInt(7), random.nextInt(8),
                if (it % 3 == 0) text.length + 8 else 0, if (it % 5 == 0) random.nextInt(1, 8) else 0) ?: return@repeat
            compare(source, copy(source))
            val length = source.processedLength
            if (length > 0) {
                val start = random.nextInt(length)
                val end = random.nextInt(start + 1, length + 1)
                // Raw BidiLine setLine also supports multi-paragraph slices;
                // public setLine guards those separately in the future facade.
                val s = result { setLine.invoke(null, source, start, end) }
                val p = BidiLineFixture()
                val a = result { BidiLineCore(copy(source)).setLine(start, end, p) }
                assertEquals(s.first, a.first)
                if (s.first == "ok") compare(s.second as com.ibm.icu.text.Bidi, p)
            }
        }
    }
    @Test fun coldControlIndexLookupKeepsSourceRunCacheFailure() {
        val source = prepare("a\u200d", 0, 0, 2)!!
        val p = copy(source)
        val core = BidiLineCore(p)
        assertEquals("bounds", result { source.getVisualIndex(0) }.first)
        assertEquals("bounds", result { core.getVisualIndex(0) }.first)
        checkState(source, p)
        assertEquals(-1, source.getVisualIndex(1)); assertEquals(-1, core.getVisualIndex(1))
        assertEquals(-1, p.runCount)
        source.countRuns(); core.getRuns()
        assertEquals(source.getVisualIndex(0), core.getVisualIndex(0))
        checkState(source, p)
    }
    @Test fun trailingStartAndOversizedLevelArrayCopyMatchSource() {
        for (text in listOf("abc   ", "א a \t", "a\n", "א\r\n", "a\u2067א\u2069 \u200d")) {
            val source = prepare(text, 0, 0, 0, text.length + 8)!!
            val p = copy(source)
            trailing.invoke(null, source); BidiLineCore(p).setTrailingWSStart()
            checkState(source, p)
            val s = getLevels.invoke(null, source) as ByteArray
            val a = BidiLineCore(p).getLevels()
            assertContentEquals(s, a)
            assertNotSame(field("levels").get(source), s); assertNotSame(p.levels, a)
            checkState(source, p)
        }
    }
}
