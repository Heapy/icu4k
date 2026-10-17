// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import java.lang.reflect.Modifier
import kotlin.random.Random
import kotlin.test.*

class BidiApiOracleTest {
    private val sourceClass = com.ibm.icu.text.Bidi::class.java
    private fun sf(name: String) = sourceClass.getDeclaredField(name).apply { isAccessible = true }
    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
    private fun run(value: Any?): Any? = value?.let { listOf(field(it.javaClass, "start").getInt(it), field(it.javaClass, "limit").getInt(it), field(it.javaClass, "level").getByte(it), field(it.javaClass, "insertRemove").getInt(it)) }
    private fun points(value: Any): Any = listOf(field(value.javaClass, "size").getInt(value), field(value.javaClass, "confirmed").getInt(value),
        (field(value.javaClass, "points").get(value) as Array<*>).map { p -> p?.let { listOf(field(it.javaClass, "pos").getInt(it), field(it.javaClass, "flag").getInt(it)) } })
    private fun isolates(value: Any?): Any? = (value as Array<*>?)?.map { p -> p?.let { listOf("startON", "start1", "stateImp", "state").map { n -> field(it.javaClass, n).get(it) } } }
    private fun result(action: () -> Any?): Pair<String, Any?> = try {
        val v = action()
        "ok" to when (v) { is ByteArray -> v.toList(); is IntArray -> v.toList(); is CharArray -> v.toList(); is com.ibm.icu.text.BidiRun, is BidiRun -> run(v); else -> v }
    } catch (e: Throwable) {
        when (e) {
            is IndexOutOfBoundsException -> "bounds" to null
            is NullPointerException -> "null" to null
            is IllegalArgumentException -> "argument:${e.message}" to null
            is IllegalStateException -> "state:${e.message}" to null
            is NegativeArraySizeException -> "negativeSize" to null
            is OutOfMemoryError -> "memory:${e.message}" to null
            is InternalError -> "internal:${e.message}" to null
            else -> throw e
        }
    }
    private fun state(s: com.ibm.icu.text.Bidi, p: Bidi) {
        for (name in listOf("originalLength", "length", "resultLength", "mayAllocateText", "mayAllocateRuns", "reorderingMode", "reorderingOptions", "paraLevel", "defaultParaLevel", "prologue", "epilogue", "direction", "flags", "lastArabicPos", "trailingWSStart", "paraCount", "runCount", "isolateCount", "isGoodLogicalToVisualRunsMap", "controlCount")) {
            assertEquals(sf(name).get(s), field(Bidi::class.java, name).get(p), name)
        }
        assertEquals(sf("isInverse").getBoolean(s), p.inverse)
        assertEquals(sf("orderParagraphsLTR").getBoolean(s), p.paragraphsLTR)
        for (name in listOf("text", "dirPropsMemory", "levelsMemory", "dirProps", "levels", "paras_limit", "paras_level", "logicalToVisualRunsMap")) {
            fun snapshot(a: Any?) = when (a) { is ByteArray -> a.toList(); is CharArray -> a.toList(); is IntArray -> a.toList(); else -> a }
            assertEquals(snapshot(sf(name).get(s)), snapshot(field(Bidi::class.java, name).get(p)), name)
        }
        for (name in listOf("runs", "runsMemory", "simpleRuns")) {
            assertEquals((sf(name).get(s) as Array<*>?)?.map(::run), (field(Bidi::class.java, name).get(p) as Array<*>?)?.map(::run), name)
        }
        assertEquals(points(sf("insertPoints").get(s)), points(p.insertPoints))
        assertEquals(isolates(sf("isolates").get(s)), isolates(p.isolates))
        for ((a, b) in listOf("levels" to "levelsMemory", "dirProps" to "dirPropsMemory", "runs" to "runsMemory", "runs" to "simpleRuns")) {
            assertEquals(sf(a).get(s) === sf(b).get(s), field(Bidi::class.java, a).get(p) === field(Bidi::class.java, b).get(p), "$a alias $b")
        }
        val parent = sf("paraBidi").get(s) as com.ibm.icu.text.Bidi?
        assertEquals(if (parent == null) 0 else if (parent === s) 1 else 2, if (p.paraBidi == null) 0 else if (p.paraBidi === p) 1 else 2)
        if (parent != null && parent !== s) assertEquals(sf("paraBidi").get(parent) === parent, p.paraBidi!!.paraBidi === p.paraBidi)
        assertEquals(sf("customClassifier").get(s) == null, p.customClassifier == null)
    }
    private inner class PairApi(val s: com.ibm.icu.text.Bidi, val p: Bidi) {
        fun call(label: String, a: () -> Any?, b: () -> Any?): Pair<String, Any?> {
            val expected = result(a); val actual = result(b)
            assertEquals(expected, actual, label)
            state(s, p)
            return expected
        }
        fun inspect() {
            call("direction", { s.direction }, { p.getDirection() })
            call("text", { s.textAsString }, { p.getTextAsString() })
            call("text array", { s.text }, { p.getText() })
            call("length", { s.length }, { p.getLength() }); call("processed", { s.processedLength }, { p.getProcessedLength() }); call("result", { s.resultLength }, { p.getResultLength() })
            call("para level", { s.paraLevel }, { p.getParaLevel() })
            call("paragraph count", { s.countParagraphs() }, { p.countParagraphs() })
            call("mixed", { s.isMixed }, { p.isMixed() }); call("ltr", { s.isLeftToRight }, { p.isLeftToRight() }); call("rtl", { s.isRightToLeft }, { p.isRightToLeft() })
            call("base ltr", { s.baseIsLeftToRight() }, { p.baseIsLeftToRight() }); call("base level", { s.baseLevel }, { p.getBaseLevel() })
            for (i in listOf(-1, 0, p.length / 2, p.length - 1, p.length, Int.MAX_VALUE)) {
                call("level $i", { s.getLevelAt(i) }, { p.getLevelAt(i) })
                call("visual index $i", { s.getVisualIndex(i) }, { p.getVisualIndex(i) })
                call("logical run $i", { s.getLogicalRun(i) }, { p.getLogicalRun(i) })
                call("paragraph $i", { s.getParagraph(i) }, { p.getParagraph(i) })
                call("paragraph index $i", { s.getParagraphIndex(i) }, { p.getParagraphIndex(i) })
            }
            for (i in listOf(-1, 0, p.paraCount - 1, p.paraCount)) call("paragraph by $i", { s.getParagraphByIndex(i) }, { p.getParagraphByIndex(i) })
            val count = call("countRuns", { s.countRuns() }, { p.countRuns() })
            if (count.first == "ok") {
                for (r in -1..p.runCount) {
                    call("visual run $r", { s.getVisualRun(r) }, { p.getVisualRun(r) })
                    call("run level $r", { s.getRunLevel(r) }, { p.getRunLevel(r) })
                    call("run start $r", { s.getRunStart(r) }, { p.getRunStart(r) })
                    call("run limit $r", { s.getRunLimit(r) }, { p.getRunLimit(r) })
                }
            }
            for (i in listOf(-1, 0, p.resultLength / 2, p.resultLength - 1, p.resultLength)) call("logical index $i", { s.getLogicalIndex(i) }, { p.getLogicalIndex(i) })
            call("logical map", { s.logicalMap }, { p.getLogicalMap() }); call("visual map", { s.visualMap }, { p.getVisualMap() })
            val sa = result { s.levels }; val pa = result { p.getLevels() }; assertEquals(sa, pa, "levels"); state(s, p)
            for (o in listOf(0, 1, 2, 3, 4, 8, 10, 16, 18, 20, 31)) call("write $o", { s.writeReordered(o) }, { p.writeReordered(o) })
        }
    }
    @Test fun publicConstantsConstructorsAndUninitializedStateMatchSource() {
        for (f in sourceClass.fields.filter { Modifier.isStatic(it.modifiers) && it.type.isPrimitive }) {
            val p = Bidi::class.java.getField(f.name)
            assertEquals(f.type, p.type, f.name); assertEquals(f.get(null), p.get(null), f.name)
        }
        PairApi(com.ibm.icu.text.Bidi(), Bidi()).inspect()
        for (flags in listOf(-2, -1, 0, 1, 2, 125, 126, 127, 128, Int.MIN_VALUE, Int.MAX_VALUE)) for (text in listOf("", "abc", "אב 12 a", "a\u2067א\u2069")) {
            PairApi(com.ibm.icu.text.Bidi(text, flags), Bidi(text, flags)).inspect()
        }
        for (length in -1..8) for (start in listOf(-1, 0, 2, 6)) for (embStart in listOf(-1, 0, 2, 7)) {
            val chars = "xxאב a".toCharArray(); val embeddings = byteArrayOf(0, -1, 2, -3, -128, 0, 1)
            val s = result { com.ibm.icu.text.Bidi(chars, start, embeddings, embStart, length, 127); null }
            val p = result { Bidi(chars, start, embeddings, embStart, length, 127); null }
            assertEquals(s, p, "ctor $start $embStart $length")
        }
        assertEquals(result { com.ibm.icu.text.Bidi(null as String?, 0) }, result { Bidi(null as String?, 0) })
        for ((a, b) in listOf(-1 to 0, 0 to -1, -1 to -1)) assertEquals(result { com.ibm.icu.text.Bidi(a, b) }, result { Bidi(a, b) })
    }
    @Test fun reusableParagraphsModesFailuresAndFixedCapacityMatchSource() {
        val random = Random(0x42494449)
        val alphabet = "abאבابت012٣٤()[] \t\r\n\u2029\u200c\u200d\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069\ud800\udc00"
        for (capacity in listOf(0 to 0, 32 to 0, 0 to 1, 0 to 4, 48 to 8)) {
            val pair = PairApi(com.ibm.icu.text.Bidi(capacity.first, capacity.second), Bidi(capacity.first, capacity.second))
            repeat(150) { iteration ->
                val mode = random.nextInt(-1, 8); val options = random.nextInt(8)
                pair.s.setReorderingMode(mode); pair.p.setReorderingMode(mode)
                pair.s.setReorderingOptions(options); pair.p.setReorderingOptions(options)
                pair.s.orderParagraphsLTR(iteration % 2 == 0); pair.p.orderParagraphsLTR(iteration % 2 == 0)
                if (iteration % 9 == 0) { pair.s.setInverse(iteration % 2 == 0); pair.p.setInverse(iteration % 2 == 0) }
                val context = if (iteration % 5 == 0) "א\na" else null
                pair.s.setContext(context, "٣"); pair.p.setContext(context, "٣")
                val text = buildString { repeat(random.nextInt(65)) { append(alphabet[random.nextInt(alphabet.length)]) } }
                val level = listOf(-1, 0, 1, 2, 125, 126, 127).random(random).toByte()
                val se = if (iteration % 4 == 0) ByteArray(random.nextInt(text.length + 1)) { listOf(0, 1, -127, 126).random(random).toByte() } else null
                val pe = se?.copyOf(); val sc = text.toCharArray(); val pc = text.toCharArray()
                pair.call("setPara iteration $iteration", { pair.s.setPara(sc, level, se) }, { pair.p.setPara(pc, level, pe) })
                assertEquals(sf("text").get(pair.s) === sc, pair.p.text === pc, "text alias")
                if (se != null) { assertContentEquals(se, pe); assertEquals(sf("levels").get(pair.s) === se, pair.p.levels === pe, "embedding alias") }
                pair.inspect()
            }
        }
    }
    @Test fun linesParentInvalidationRecoveryAndArrayAliasesMatchSource() {
        val pair = PairApi(com.ibm.icu.text.Bidi(), Bidi())
        pair.call("parent", { pair.s.setPara("a אב\nא a", 126, null) }, { pair.p.setPara("a אב\nא a", 126, null) })
        val child = PairApi(pair.s.setLine(0, 4), pair.p.setLine(0, 4)); child.inspect()
        for ((start, limit) in listOf(0 to 0, -1 to 2, 0 to 8, 4 to 8, 5 to 8)) {
            val s = result { pair.s.setLine(start, limit); null }; val p = result { pair.p.setLine(start, limit); null }; assertEquals(s, p)
        }
        pair.call("invalid embeddings", { pair.s.setPara("abc", 0, byteArrayOf(126)) }, { pair.p.setPara("abc", 0, byteArrayOf(126)) })
        child.inspect()
        pair.call("recover", { pair.s.setPara("xy", 1, null) }, { pair.p.setPara("xy", 1, null) })
        child.inspect()
        pair.call("alias setup", { pair.s.setPara("a אב", 0, null) }, { pair.p.setPara("a אב", 0, null) })
        val sa = pair.s.levels; val pa = pair.p.getLevels()
        assertEquals(sa === sf("levels").get(pair.s), pa === pair.p.levels)
        sa[0] = 2; pa[0] = 2
        pair.s.text[0] = 'ב'; pair.p.getText()[0] = 'ב'
        pair.inspect()
    }
}
