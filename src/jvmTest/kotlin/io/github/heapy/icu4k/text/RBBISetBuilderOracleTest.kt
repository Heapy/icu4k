package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.CodePointTrie
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.random.Random
import kotlin.test.*

class RBBISetBuilderOracleTest {
    private fun get(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun call(target: Any, name: String, vararg args: Any): Any? = target.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
        .apply { isAccessible = true }.invoke(target, *args)
    private fun capture(block: () -> Unit): String {
        val old = System.out
        val out = ByteArrayOutputStream()
        try { System.setOut(PrintStream(out)); block() } finally { System.setOut(old) }
        return out.toString(Charsets.UTF_8)
    }
    private fun compareTree(j: Any?, k: RBBINode?) {
        if (j == null) { assertNull(k); return }
        assertNotNull(k)
        k.fSerialNum = get(j, "fSerialNum") as Int
        for ((field, value) in mapOf("fType" to k.fType, "fVal" to k.fVal, "fPrecedence" to k.fPrecedence)) assertEquals(get(j, field), value, field)
        assertEquals(get(j, "fParent")?.let { get(it, "fType") }, k.fParent?.fType)
        assertEquals(get(j, "fParent")?.let { get(it, "fVal") }, k.fParent?.fVal)
        compareTree(get(j, "fLeftChild"), k.fLeftChild); compareTree(get(j, "fRightChild"), k.fRightChild)
    }

    private fun compare(rules: String, merge: Boolean = false, debug: Boolean = false): RBBISetBuilder {
        val type = Class.forName("com.ibm.icu.text.RBBIRuleBuilder")
        val original = type.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(rules)
        call(get(original, "fScanner")!!, "parse")
        val j = get(original, "fSetBuilder")!!
        val result = RBBIRuleParseResult(rules)
        RBBIRuleScanner(result).parse()
        val k = RBBISetBuilder(result)
        call(j, "buildRanges"); k.buildRanges()
        val jSets = get(original, "fUSetNodes") as List<*>
        assertEquals(jSets.size, result.fUSetNodes.size)
        for (i in jSets.indices) compareTree(jSets[i], result.fUSetNodes[i])
        fun ranges() {
            for ((field, value) in mapOf("fGroupCount" to k.fGroupCount, "fDictCategoriesStart" to k.fDictCategoriesStart, "fSawBOF" to k.fSawBOF))
                assertEquals(get(j, field), value, "$rules: $field")
            var jr = get(j, "fRangeList")
            var kr = k.fRangeList
            while (jr != null) {
                val range = assertNotNull(kr)
                for ((field, value) in mapOf("fStartChar" to range.fStartChar, "fEndChar" to range.fEndChar,
                    "fNum" to range.fNum, "fIncludesDict" to range.fIncludesDict, "fFirstInGroup" to range.fFirstInGroup))
                    assertEquals(get(jr, field), value, field)
                assertEquals((get(jr, "fIncludesSets") as List<*>).map { jSets.indexOf(it) }, range.fIncludesSets.map { result.fUSetNodes.indexOf(it) })
                assertEquals(call(jr, "isDictionaryRange"), range.isDictionaryRange())
                jr = get(jr, "fNext"); kr = range.fNext
            }
            assertNull(kr)
            for (category in 0..k.getNumCharCategories()) assertEquals(call(j, "getFirstChar", category), k.getFirstChar(category))
        }
        ranges()
        if (debug) for (method in listOf("printRanges", "printRangeGroups", "printSets")) {
            val expected = capture { call(j, method) }
            val actual = capture { when (method) { "printRanges" -> k.printRanges(); "printRangeGroups" -> k.printRangeGroups(); else -> k.printSets() } }
            assertEquals(expected, actual, method)
        }
        if (merge) {
            val pairClass = Class.forName("com.ibm.icu.text.RBBIRuleBuilder\$IntPair")
            fun mergePair(first: Int, second: Int) {
                val pair = pairClass.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.newInstance(first, second)
                call(j, "mergeCategories", pair); k.mergeCategories(first, second)
                ranges()
            }
            if (k.fDictCategoriesStart > 4) mergePair(3, k.fDictCategoriesStart - 1)
            if (k.getNumCharCategories() - k.fDictCategoriesStart > 1) mergePair(k.fDictCategoriesStart, k.getNumCharCategories() - 1)
        }
        call(j, "buildTrie"); k.buildTrie()
        val bytes = ByteArrayOutputStream()
        call(j, "serializeTrie", bytes)
        assertContentEquals(bytes.toByteArray(), k.serializeTrie(), rules)
        assertEquals(call(j, "getTrieSize"), k.getTrieSize())
        assertNull(get(j, "fTrie")); assertNull(k.fTrie)
        assertSame(k.fFrozenTrie, k.fFrozenTrie.also { k.freezeTrieIfNotYet() })
        return k
    }

    @Test fun categoriesDictionaryMembershipBoundarySymbolsAndMergingMatchOriginal() {
        val rules = listOf("a;", ".;", "[a-c];[b-d];[a-c];", "\$dictionary=[b-d];[a-c{bof}{eof}];\$dictionary;.;",
            "\$x=[a-z];\$dictionary=\$x;\$dictionary;.;", "\$x=[a-z];\$dictionary=[a-z];\$dictionary;.;",
            "[a{bof}];[b{eof}];", "[\\u0000\\U0010ffff{bof}{eof}];.;", "[a{unused}];")
        for (rule in rules) compare(rule, merge = true, debug = true)
        val random = Random(918731)
        repeat(100) {
            val rule = buildString {
                append("\$dictionary=[c-m];")
                repeat(random.nextInt(1, 12)) {
                    val start = random.nextInt(97, 123)
                    val end = random.nextInt(start, 123)
                    append(if (start == end) "[${start.toChar()}];" else "[${start.toChar()}-${end.toChar()}];")
                }
                append("\$dictionary;.;")
            }
            compare(rule, merge = true)
        }
    }

    @Test fun originalRuleSourcesProduceIdenticalSerializedTries() {
        for (name in listOf("char", "word", "word_POSIX", "line", "line_cj", "line_loose", "line_loose_cj", "line_normal",
            "line_normal_cj", "line_phrase_cj", "line_loose_phrase_cj", "line_normal_phrase_cj", "sent", "sent_el", "title")) {
            compare(RBBIDataWrapper.get(BinaryBuffer(ICUData.getBytes("brkitr/$name.brk")!!)).fRuleSource)
        }
    }

    @Test fun valueWidthThresholdMatchesOriginal() {
        for (count in listOf(251, 252, 300)) {
            val rules = buildString { repeat(count) { append("[\\u${(0x1000 + it).toString(16).padStart(4, '0')}];") } }
            val builder = compare(rules)
            assertEquals(count + 4, builder.getNumCharCategories())
            assertEquals(if (count == 251) CodePointTrie.ValueWidth.BITS_8 else CodePointTrie.ValueWidth.BITS_16, builder.fFrozenTrie!!.getValueWidth())
        }
    }
}
