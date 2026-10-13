package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.util.BinaryBuffer
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.*

class RBBITableBuilderOracleTest {
    private fun get(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun call(target: Any, name: String, vararg args: Any): Any? = target.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
        .apply { isAccessible = true }.invoke(target, *args)
    private fun capture(block: () -> Unit): String {
        val old = System.out; val bytes = ByteArrayOutputStream()
        try { System.setOut(PrintStream(bytes)); block() } finally { System.setOut(old) }
        return bytes.toString(Charsets.UTF_8)
    }
    private fun compare(rules: String, debug: Boolean = false): RBBITableBuilder {
        val ownerType = Class.forName("com.ibm.icu.text.RBBIRuleBuilder")
        val owner = ownerType.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(rules)
        try { call(get(owner, "fScanner")!!, "parse") } catch (e: java.lang.reflect.InvocationTargetException) { throw AssertionError("Original rejected: $rules", e.cause) }
        call(get(owner, "fSetBuilder")!!, "buildRanges")
        val tableType = Class.forName("com.ibm.icu.text.RBBITableBuilder")
        val j = tableType.getDeclaredConstructor(ownerType, Int::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(owner, 0)
        ownerType.getDeclaredField("fForwardTable").apply { isAccessible = true }.set(owner, j)
        val result = RBBIRuleParseResult(rules)
        val scanner = RBBIRuleScanner(result); scanner.parse()
        val sets = RBBISetBuilder(result); sets.buildRanges()
        val k = RBBITableBuilder(result, sets, scanner.numRules())
        call(j, "buildForwardTable"); k.buildForwardTable()
        val jNodes = IdentityHashMap<Any, Int>()
        val kNodes = IdentityHashMap<RBBINode, Int>()
        val pairs = ArrayList<Pair<Any, RBBINode>>()
        fun tree(jn: Any?, kn: RBBINode?) {
            if (jn == null) { assertNull(kn); return }
            assertNotNull(kn)
            assertEquals(jNodes[jn], kNodes[kn])
            if (jNodes.containsKey(jn)) return
            jNodes[jn] = pairs.size; kNodes[kn] = pairs.size; pairs.add(jn to kn)
            kn.fSerialNum = get(jn, "fSerialNum") as Int
            for ((field, value) in mapOf("fType" to kn.fType, "fVal" to kn.fVal, "fNullable" to kn.fNullable,
                "fRuleRoot" to kn.fRuleRoot, "fChainIn" to kn.fChainIn, "fLookAheadEnd" to kn.fLookAheadEnd)) assertEquals(get(jn, field), value, "$rules: $field")
            tree(get(jn, "fLeftChild"), kn.fLeftChild); tree(get(jn, "fRightChild"), kn.fRightChild)
        }
        tree((get(owner, "fTreeRoots") as Array<*>)[0], result.fTreeRoots[0])
        for ((jn, kn) in pairs) {
            assertEquals(get(jn, "fParent")?.let { jNodes[it] }, kn.fParent?.let { kNodes[it] })
            for ((field, values) in listOf("fFirstPosSet" to kn.fFirstPosSet, "fLastPosSet" to kn.fLastPosSet, "fFollowPos" to kn.fFollowPos)) {
                val expected = (get(jn, field) as Set<*>).map { assertNotNull(jNodes[it]) }.sorted()
                assertEquals(expected, values.map { assertNotNull(kNodes[it]) }.sorted(), "$rules: $field")
            }
        }
        fun states() {
            val expected = get(j, "fDStates") as List<*>
            assertEquals(expected.size, k.fDStates.size, rules)
            for (i in expected.indices) {
                val js = expected[i]!!; val ks = k.fDStates[i]
                for ((field, value) in mapOf("fMarked" to ks.fMarked, "fAccepting" to ks.fAccepting,
                    "fLookAhead" to ks.fLookAhead, "fTagsIdx" to ks.fTagsIdx, "fTagVals" to ks.fTagVals)) assertEquals(get(js, field), value, "$rules: state $i $field")
                assertContentEquals(get(js, "fDtran") as IntArray, ks.fDtran, "$rules: state $i")
                assertEquals((get(js, "fPositions") as Set<*>).map { jNodes[it] }.sortedBy { it }, ks.fPositions.map { kNodes[it] }.sortedBy { it })
            }
            assertEquals(get(owner, "fRuleStatusVals"), result.fRuleStatusVals)
            assertEquals(get(owner, "fStatusSets"), result.fStatusSets)
            assertContentEquals(get(j, "fLookAheadRuleMap") as IntArray, k.fLookAheadRuleMap)
            assertEquals(get(j, "fLASlotsInUse"), k.fLASlotsInUse)
        }
        fun table(expected: Any, actual: RBBIDataWrapper.RBBIStateTable) {
            for ((field, value) in mapOf("fNumStates" to actual.fNumStates, "fRowLen" to actual.fRowLen,
                "fDictCategoriesStart" to actual.fDictCategoriesStart, "fLookAheadResultsSize" to actual.fLookAheadResultsSize,
                "fFlags" to actual.fFlags)) assertEquals(get(expected, field), value, field)
            assertContentEquals(get(expected, "fTable") as CharArray, actual.fTable)
            val binary = ByteArrayOutputStream()
            call(expected, "put", java.io.DataOutputStream(binary))
            assertContentEquals(binary.toByteArray(), actual.toBinary())
        }
        fun debugOutput() {
            if (!debug) return
            for (method in listOf("printStates", "printRuleStatusTable", "printReverseTable")) {
                val expected = capture { call(j, method) }
                val actual = capture { when (method) { "printStates" -> k.printStates(); "printRuleStatusTable" -> k.printRuleStatusTable(); else -> k.printReverseTable() } }
                assertEquals(expected, actual, method)
            }
        }
        states(); table(call(j, "exportTable")!!, k.exportTable()); debugOutput()
        call(owner, "optimizeTables")
        var changed: Boolean
        do {
            changed = false
            val pair = RBBITableBuilder.IntPair(3, 0)
            while (k.findDuplCharClassFrom(pair)) { sets.mergeCategories(pair.first, pair.second); k.removeColumn(pair.second); changed = true }
            while (k.removeDuplicateStates() > 0) changed = true
        } while (changed)
        states(); table(call(j, "exportTable")!!, k.exportTable())
        assertEquals(call(j, "getTableSize"), k.getTableSize())
        call(j, "buildSafeReverseTable"); k.buildSafeReverseTable()
        val safe = get(j, "fSafeTable") as List<*>
        assertEquals(safe.size, k.fSafeTable!!.size)
        for (i in safe.indices) assertContentEquals(safe[i] as ShortArray, k.fSafeTable!![i])
        table(call(j, "exportSafeTable")!!, k.exportSafeTable())
        assertEquals(call(j, "getSafeTableSize"), k.getSafeTableSize())
        debugOutput()
        return k
    }

    @Test fun expressionsChainingBofLookAheadAndTagsMatchOriginal() {
        val examples = listOf("a;", ".;", "(a|b)*c+;", "a?b;", "a/b{10};ab{20};", "a/b;c/d;", "a/b;a/c;",
            "!!chain; ab;bc;cd;", "!!chain; ^ab;bc;", "[a{bof}]b{10};.;", "[a{eof}];.;",
            "!!lookAheadHardBreak; a/b{3};", "a{20};a{10};a{0};", "\$dictionary=[c-f]; \$dictionary+{100};.;",
            "\$x=[a-c];\$y=\$x+;\$y{42};.;")
        for (rules in examples) compare(rules, true)
        val random = Random(381557)
        val terms = listOf("a", "[a-c]", "(a|b)", "'hi'", "[\\p{N}]", ".")
        repeat(100) {
            val rules = buildString {
                if (random.nextBoolean()) append("!!chain;")
                repeat(random.nextInt(1, 7)) {
                    repeat(random.nextInt(1, 5)) { append(terms.random(random)); append(listOf("", "*", "+", "?").random(random)) }
                    if (random.nextBoolean()) append("{${random.nextInt(100)}}")
                    append(';')
                }
            }
            compare(rules)
        }
    }
    @Test fun allEmbeddedRuleSourcesMatchOriginalDfaAndSafeTables() {
        for (name in listOf("char", "word", "word_POSIX", "line", "line_cj", "line_loose", "line_loose_cj", "line_normal",
            "line_normal_cj", "line_phrase_cj", "line_loose_phrase_cj", "line_normal_phrase_cj", "sent", "sent_el", "title")) {
            compare(RBBIDataWrapper.get(BinaryBuffer(ICUData.getBytes("brkitr/$name.brk")!!)).fRuleSource)
        }
    }
    @Test fun forwardTableWidthBoundaryMatchesOriginal() {
        for (length in listOf(253, 254, 300)) {
            val table = compare("'${"a".repeat(length)}';")
            assertEquals(length + 2, table.exportTable().fNumStates)
            assertEquals(length == 253, table.exportTable().fFlags and RBBIDataWrapper.RBBI_8BITS_ROWS != 0)
        }
    }
}
