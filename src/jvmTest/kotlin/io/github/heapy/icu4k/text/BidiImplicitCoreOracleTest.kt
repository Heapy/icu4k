// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class BidiImplicitCoreOracleTest {
    private val clazz = com.ibm.icu.text.Bidi::class.java
    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
    private fun field(name: String) = field(clazz, name)
    private fun outcome(action: () -> Unit): String = try { action(); "ok" } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        when (cause) {
            is IndexOutOfBoundsException -> "bounds"
            is NullPointerException -> "null"
            is IllegalArgumentException -> "argument:${cause.message}"
            is IllegalStateException -> "state:${cause.message}"
            else -> throw cause
        }
    }
    private fun snapshotPoints(points: Any): List<Any?> {
        val type = points.javaClass
        val rows = (field(type, "points").get(points) as Array<*>).map { p -> p?.let { listOf(field(it.javaClass, "pos").getInt(it), field(it.javaClass, "flag").getInt(it)) } }
        return listOf(field(type, "size").getInt(points), field(type, "confirmed").getInt(points), rows)
    }
    private fun snapshotPortPoints(points: BidiImplicitCore.InsertPoints): List<Any?> = listOf(points.size, points.confirmed, points.points.map { it?.let { listOf(it.pos, it.flag) } })
    private fun snapshotIsolates(isolates: Any?): List<Any?>? = (isolates as Array<*>?)?.map { i -> i?.let {
        listOf("startON", "start1", "stateImp", "state").map { name -> field(it.javaClass, name).get(it) }
    } }
    private fun snapshotPortIsolates(isolates: Array<BidiImplicitCore.Isolate?>?): List<Any?>? = isolates?.map { it?.let { listOf(it.startON, it.start1, it.stateImp, it.state) } }
    private fun compare(text: String, level: Int, mode: Int, options: Int, order: Boolean, prologue: String?, epilogue: String?, embeddings: ByteArray? = null, contextMutation: Pair<Boolean, String?>? = null) {
        val sc = mutableListOf<Int>(); val pc = mutableListOf<Int>()
        val source = object : com.ibm.icu.text.Bidi() {
            override fun getCustomizedClass(c: Int): Int {
                sc += c
                if (c == '.'.code && contextMutation != null) {
                    if (contextMutation.first) setContext(contextMutation.second, epilogue)
                    else setContext(prologue, contextMutation.second)
                }
                return super.getCustomizedClass(c)
            }
        }
        source.setReorderingMode(mode); source.setReorderingOptions(options); source.orderParagraphsLTR(order); source.setContext(prologue, epilogue)
        lateinit var base: BidiPropertyFixture
        lateinit var port: BidiImplicitFixture
        base = BidiPropertyFixture(text, level, mode, BidiReorderingCore.normalizeReorderingOptions(options), order, prologue?.takeIf { it.isNotEmpty() }) {
            pc += it
            if (it == '.'.code && contextMutation != null) {
                val replacement = contextMutation.second?.takeIf { value -> value.isNotEmpty() }
                if (contextMutation.first) base.prologue = replacement else port.epilogue = replacement
            }
            BidiPropertyCore.customizedClass(it, null)
        }
        port = BidiImplicitFixture(base, epilogue?.takeIf { it.isNotEmpty() })
        val se = embeddings?.copyOf()
        val pe = embeddings?.copyOf()
        val expected = outcome { source.setPara(text.toCharArray(), level.toByte(), se) }
        val actual = outcome { port.resolveFresh(pe) }
        val label = "mode=$mode options=$options level=$level order=$order text=$text"
        assertEquals(expected, actual, label)
        assertEquals(sc, pc, "$label callbacks")
        assertContentEquals(field("dirProps").get(source) as ByteArray, base.dirProps, label)
        assertContentEquals(field("levels").get(source) as ByteArray, base.levels, label)
        assertContentEquals(field("paras_limit").get(source) as IntArray, base.paraLimits, label)
        assertContentEquals(field("paras_level").get(source) as ByteArray, base.paraLevels, label)
        for ((name, value) in listOf("length" to base.length, "paraCount" to base.paraCount, "flags" to base.flags,
            "lastArabicPos" to base.lastArabicPos, "controlCount" to base.controlCount, "isolateCount" to base.isolateCount, "trailingWSStart" to port.trailingWSStart)) {
            assertEquals(field(name).getInt(source), value, "$label $name")
        }
        assertEquals(field("paraLevel").getByte(source), base.paraLevel, label)
        assertEquals(snapshotPoints(field("insertPoints").get(source)), snapshotPortPoints(port.insertPoints), label)
        assertEquals(snapshotIsolates(field("isolates").get(source)), snapshotPortIsolates(port.isolates), label)
        if (se != null && field("levels").get(source) === se) {
            assertSame(pe, base.levels, "$label caller embedding alias")
            assertContentEquals(se, pe, "$label caller embedding mutation")
        }
    }
    @Test fun publicOptionNormalizationRetainsOtherFlags() {
        val source = com.ibm.icu.text.Bidi()
        for (options in (0..255).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE, -1, -2, -3)) {
            source.setReorderingOptions(options)
            assertEquals(source.reorderingOptions, BidiReorderingCore.normalizeReorderingOptions(options))
        }
    }
    @Test fun preparedParagraphsAndFullImplicitSchedulingMatchPublicSource() {
        val texts = listOf("abc", "אבג", "a(אב)12 ب٣ z", "ا 12 א 34 ا 56 a", "a\r\nאב\na\u2029ب",
            "a\u202bא(12)\u202c z", "\u202da(אב)\u202cب", "a\u2067אב \u2066x\u2069z\u2069 q",
            "\u2069א", "a\u2068א\u2069", "\u0300א\u0301 12", "😀𞤀𞸀\ud800X\udc00",
            "א a   \n", "a\u200c\u200d\u200e\u200fא\u202c ", "\u2067".repeat(140) + "aא" + "\u2069".repeat(145))
        for (text in texts) for (level in listOf(0, 1, 2, 125, 126, 127)) for (mode in listOf(0, 1, 2, 4, 5, 6)) for (options in listOf(0, 1, 2, 3, 4, 6)) {
            compare(text, level, mode, options, mode % 2 == 0, null, null)
        }
        for (prologue in listOf(null, "", "a", "א", "ا\na", "a\nا", "😀𞤀", "\ud800א\udc00")) {
            for (epilogue in listOf(null, "", "a", "א", "12", "٣", "\nא", "😀𞸀")) for (mode in listOf(0, 1, 2, 4, 5, 6)) {
                compare("12 (א) a ا", 1, mode, 1, false, prologue, epilogue)
            }
        }
    }
    @Test fun randomParagraphsAndSuppliedEmbeddingsMatchPublicSource() {
        val random = Random(0x4931)
        val alphabet = "abאבابت012٣٤.,:+-()[]{}\u0300\u05b0 \t\r\n\u2029\u200c\u200d\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069\ud800\udc00"
        repeat(10000) {
            fun text(size: Int) = buildString { repeat(size) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val text = text(random.nextInt(1, 100))
            val embedding = if (it % 3 == 0) ByteArray(if (it % 9 == 0) random.nextInt(text.length) else text.length) { listOf(0, 1, 2, 3, -128, -127, -126, 126).random(random).toByte() } else null
            compare(text, listOf(0, 1, 2, 125, 126, 127).random(random), listOf(0, 1, 2, 4, 5, 6).random(random), random.nextInt(8), random.nextBoolean(),
                if (random.nextBoolean()) null else text(random.nextInt(15)), if (random.nextBoolean()) null else text(random.nextInt(15)), embedding)
        }
    }
    @Test fun contextReplacementDuringImplicitCallbacksMatchesSource() {
        for (replacement in listOf("b", "", null, "😀א", "abc אב")) for (mode in listOf(0, 1, 2, 4, 5, 6)) {
            compare("1 א", 1, mode, 1, false, "אabc.", null, contextMutation = true to replacement)
            compare("1 א", 1, mode, 1, false, null, ".abcא", contextMutation = false to replacement)
        }
    }
    @Test fun retainedIsolateAndPointStorageMatchesRepeatedPublicCalls() {
        val source = com.ibm.icu.text.Bidi()
        source.setPara("a\u2067א\u2069b", 0, null)
        val first = BidiImplicitFixture(BidiPropertyFixture("a\u2067א\u2069b", 0, 0, 0, false))
        first.resolveFresh(null)
        val sourceIsolates = field("isolates").get(source)
        val portIsolates = first.isolates
        assertEquals(snapshotIsolates(sourceIsolates), snapshotPortIsolates(portIsolates))
        source.setReorderingMode(5); source.setReorderingOptions(1)
        val second = BidiImplicitFixture(BidiPropertyFixture("\u2069אabc", 0, 5, 1, false))
        second.isolates = portIsolates
        assertEquals("bounds", outcome { source.setPara("\u2069אabc", 0, null) })
        assertEquals("bounds", outcome { second.resolveFresh(null) })
        assertSame(sourceIsolates, field("isolates").get(source))
        assertSame(portIsolates, second.isolates)
        assertEquals(snapshotIsolates(field("isolates").get(source)), snapshotPortIsolates(second.isolates))
        assertContentEquals(field("levels").get(source) as ByteArray, second.base.levels)
        val fresh = com.ibm.icu.text.Bidi().apply { setReorderingMode(5); setReorderingOptions(1) }
        assertEquals("null", outcome { fresh.setPara("\u2069אabc", 0, null) })

        val marked = "ا 12 a 34 אב 56 a .".repeat(8)
        source.setPara(marked, 0, null)
        val markedPort = BidiImplicitFixture(BidiPropertyFixture(marked, 0, 5, 1, false))
        markedPort.isolates = second.isolates
        markedPort.resolveFresh(null)
        val sourcePoints = field("insertPoints").get(source)
        val sourceArray = field(sourcePoints.javaClass, "points").get(sourcePoints)
        assertEquals(snapshotPoints(sourcePoints), snapshotPortPoints(markedPort.insertPoints))
        val reused = BidiImplicitFixture(BidiPropertyFixture("abc", 0, 5, 1, false))
        reused.insertPoints.points = markedPort.insertPoints.points
        reused.isolates = markedPort.isolates
        source.setPara("abc", 0, null)
        reused.resolveFresh(null)
        assertSame(sourceArray, field(sourcePoints.javaClass, "points").get(sourcePoints))
        assertSame(markedPort.insertPoints.points, reused.insertPoints.points)
        assertEquals(snapshotPoints(sourcePoints), snapshotPortPoints(reused.insertPoints))
        assertEquals(snapshotIsolates(field("isolates").get(source)), snapshotPortIsolates(reused.isolates))
        assertEquals(0, reused.insertPoints.size)
        assertEquals(20, reused.insertPoints.points.size)
    }
    @Test fun everyPropertySequenceActionAndPartialMutationMatchesSource() {
        val levClass = Class.forName("com.ibm.icu.text.Bidi\$LevState")
        val ctor = levClass.getDeclaredConstructor().apply { isAccessible = true }
        val process = clazz.getDeclaredMethod("processPropertySeq", levClass, Short::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val random = Random(0x4932)
        for (mode in listOf(0, 1, 2, 4, 5, 6)) for (marks in listOf(0, 1)) for (parity in 0..1) {
            val pair = BidiImplicitFixture.selectPair(mode, marks)!!
            val pairName = when (mode) { 0 -> "DEFAULT"; 1 -> "NUMBERS_SPECIAL"; 2 -> "GROUP_NUMBERS_WITH_R"; 4 -> "INVERSE_NUMBERS_AS_L"; 5 -> "INVERSE_LIKE_DIRECT"; else -> "INVERSE_FOR_NUMBERS_SPECIAL" } + if (mode >= 5 && marks == 1) "_WITH_MARKS" else ""
            val sourcePair = field("impTab_$pairName").get(null)
            val sourceTables = field(sourcePair.javaClass, "imptab").get(sourcePair) as Array<*>
            val sourceActions = field(sourcePair.javaClass, "impact").get(sourcePair) as Array<*>
            for (stateIndex in pair.imptab[parity].indices) for (property in 0..6) repeat(10) {
                val source = com.ibm.icu.text.Bidi()
                source.setReorderingMode(mode)
                val base = BidiPropertyFixture("a".repeat(40), 0, mode, marks, false)
                for (i in base.levels.indices) { base.levels[i] = random.nextInt(6).toByte(); base.dirProps[i] = listOf(0, 1, 5, 20, 21, 22).random(random).toByte() }
                field("levels").set(source, base.levels.copyOf()); field("dirProps").set(source, base.dirProps.copyOf())
                val port = BidiImplicitFixture(base); val core = BidiImplicitCore(port)
                val state = BidiImplicitCore.LevState()
                state.impTab = pair.imptab[parity]; state.impAct = pair.impact[parity]
                state.state = stateIndex.toShort(); state.runLevel = parity.toByte()
                state.startON = random.nextInt(-1, 20); state.startL2EN = random.nextInt(-2, 20); state.lastStrongRTL = random.nextInt(-1, 20); state.runStart = random.nextInt(20)
                val ss = ctor.newInstance()
                field(levClass, "impTab").set(ss, sourceTables[parity]); field(levClass, "impAct").set(ss, sourceActions[parity])
                for ((name, value) in listOf("state" to state.state, "runLevel" to state.runLevel, "startON" to state.startON, "startL2EN" to state.startL2EN, "lastStrongRTL" to state.lastStrongRTL, "runStart" to state.runStart)) field(levClass, name).set(ss, value)
                val start = random.nextInt(20, 35); val limit = random.nextInt(start, 41)
                assertEquals(outcome { process.invoke(source, ss, property.toShort(), start, limit) }, outcome { core.processPropertySeq(state, property.toShort(), start, limit) })
                assertContentEquals(field("levels").get(source) as ByteArray, base.levels)
                for ((name, value) in listOf("state" to state.state, "runLevel" to state.runLevel, "startON" to state.startON, "startL2EN" to state.startL2EN, "lastStrongRTL" to state.lastStrongRTL, "runStart" to state.runStart)) assertEquals(field(levClass, name).get(ss), value, name)
                assertEquals(snapshotPoints(field("insertPoints").get(source)), snapshotPortPoints(port.insertPoints))
            }
        }
    }
}
