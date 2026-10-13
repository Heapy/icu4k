package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.util.BinaryBuffer
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.*

class RBBIRuleScannerOracleTest {
    private val builderClass = Class.forName("com.ibm.icu.text.RBBIRuleBuilder")
    private fun get(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun call(target: Any, name: String, vararg args: Any): Any? = target.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
        .apply { isAccessible = true }.invoke(target, *args)
    private fun builder(rules: String): Any = builderClass.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(rules)

    private fun compare(rules: String) {
        val original = builder(rules)
        val js = get(original, "fScanner")!!
        val result = RBBIRuleParseResult(rules)
        val ks = RBBIRuleScanner(result)
        val expected = runCatching { call(js, "parse") }.exceptionOrNull()?.let { (it as java.lang.reflect.InvocationTargetException).cause!! }
        val actual = runCatching { ks.parse() }.exceptionOrNull()
        if (expected != null) {
            assertNotNull(actual, "accepted invalid rules: $rules")
            assertEquals(expected.javaClass, actual.javaClass, rules)
            assertEquals(expected.message, actual.message, rules)
        } else assertNull(actual, "$rules: $actual")
        for ((field, value) in mapOf("fScanIndex" to ks.fScanIndex, "fNextIndex" to ks.fNextIndex,
            "fLineNum" to ks.fLineNum, "fCharNum" to ks.fCharNum, "fLastChar" to ks.fLastChar,
            "fQuoteMode" to ks.fQuoteMode, "fRuleNum" to ks.fRuleNum, "fStackPtr" to ks.fStackPtr,
            "fNodeStackPtr" to ks.fNodeStackPtr, "fReverseRule" to ks.fReverseRule,
            "fLookAheadRule" to ks.fLookAheadRule, "fNoChainInRule" to ks.fNoChainInRule)) assertEquals(get(js, field), value, "$rules: $field")
        assertEquals(get(original, "fStrippedRules").toString(), result.fStrippedRules.toString(), rules)
        for ((field, value) in mapOf("fDefaultTree" to result.fDefaultTree, "fChainRules" to result.fChainRules,
            "fLookAheadHardBreak" to result.fLookAheadHardBreak)) assertEquals(get(original, field), value, rules)
        if (expected != null) return

        // Traverse every edge, including parents and shared sets/definitions, with bijective identity checks.
        val javaToKotlin = IdentityHashMap<Any, RBBINode>()
        val kotlinToJava = IdentityHashMap<RBBINode, Any>()
        val queue = ArrayList<Pair<Any, RBBINode>>()
        fun pair(j: Any?, k: RBBINode?) {
            if (j == null) { assertNull(k, rules); return }
            assertNotNull(k, rules)
            val previous = javaToKotlin[j]
            if (previous != null) { assertSame(previous, k, rules); return }
            assertNull(kotlinToJava[k], rules)
            javaToKotlin[j] = k; kotlinToJava[k] = j
            queue.add(j to k)
        }
        val roots = get(original, "fTreeRoots") as Array<*>
        for (i in roots.indices) pair(roots[i], result.fTreeRoots[i])
        val sets = get(original, "fUSetNodes") as List<*>
        assertEquals(sets.size, result.fUSetNodes.size, rules)
        for (i in sets.indices) pair(sets[i], result.fUSetNodes[i])
        val jSymbols = get(js, "fSymbolTable")!!
        val definitions = get(jSymbols, "fHashTable") as Map<*, *>
        for (key in definitions.keys.sortedBy { it.toString() }) pair(call(jSymbols, "lookupNode", key!!), ks.fSymbolTable.lookupNode(key as String))
        var index = 0
        while (index < queue.size) {
            val (j, k) = queue[index++]
            for ((field, value) in mapOf("fType" to k.fType, "fPrecedence" to k.fPrecedence, "fText" to k.fText,
                "fFirstPos" to k.fFirstPos, "fLastPos" to k.fLastPos, "fVal" to k.fVal,
                "fNullable" to k.fNullable, "fRuleRoot" to k.fRuleRoot, "fChainIn" to k.fChainIn,
                "fLookAheadEnd" to k.fLookAheadEnd)) assertEquals(get(j, field), value, "$rules: $field")
            assertEquals((get(j, "fInputSet") as com.ibm.icu.text.UnicodeSet?)?.toPattern(false), k.fInputSet?.toPattern(false), rules)
            pair(get(j, "fLeftChild"), k.fLeftChild); pair(get(j, "fRightChild"), k.fRightChild); pair(get(j, "fParent"), k.fParent)
        }
    }

    @Test fun expressionsOptionsVariablesAndErrorsMatchOriginal() {
        val examples = listOf("", "a;", "a", "(a|b)*c+;", "'a b';", "'';", "a/b{42};", "a{2147483647};", "a{2147483648};",
            "a{bad};", "a{12;", "(a;", "a);", "[a-z];", "[];", "[{ab}];", "[{ab}c];", "[a-];", "[a;", "\\p{L}+;",
            "\$x=[a-z]; \$y=\$x; [\$y]+;", "\$x=ab; [\$x];", "\$x=a;\$x=b;a;", "\$x;", "\$1=a;a;", "\$x=;a;",
            "!!chain; ^a; b;", "!!lookAheadHardBreak; a/b;", "!!unknown;a;", "!!quoted_literals_only; a;",
            "!!quoted_literals_only; 'a'; !!unquoted_literals; b;", "a; !b; !!reverse; c; !!safe_forward; d; !!safe_reverse; e; !!forward; f;",
            "# comment\r\na; #last", "'a\nb';", "\\u0041;", "\\uXX;", "😀;", "\\U0001f600;", "\ud800;", "a\udc00;",
            "(".repeat(110) + "a" + ")".repeat(110) + ";")
        for (rules in examples) compare(rules)
        val random = Random(178305)
        val terms = listOf("a", "b", "[a-z]", "'hi'", ".", "\\p{N}", "(a|b)", "[😀]", "\$x")
        repeat(250) {
            val rules = buildString {
                append("\$x=[xy];")
                repeat(random.nextInt(1, 6)) {
                    repeat(random.nextInt(1, 5)) {
                        append(terms.random(random)); append(listOf("", "*", "+", "?").random(random))
                    }
                    if (random.nextBoolean()) append("{${random.nextInt(1000)}}")
                    append(';')
                }
            }
            compare(rules)
        }
        val alphabet = "ab;()|*+?[]{}=\$!^/#' 012\n\\"
        repeat(400) { compare(buildString { repeat(random.nextInt(1, 35)) { append(alphabet.random(random)) } }) }
    }

    @Test fun allEmbeddedIcuRuleSourcesMatchOriginalTrees() {
        for (name in listOf("char", "word", "word_POSIX", "line", "line_cj", "line_loose", "line_loose_cj", "line_normal",
            "line_normal_cj", "line_phrase_cj", "line_loose_phrase_cj", "line_normal_phrase_cj", "sent", "sent_el", "title")) {
            val rules = RBBIDataWrapper.get(BinaryBuffer(ICUData.getBytes("brkitr/$name.brk")!!)).fRuleSource
            compare(rules)
        }
    }

    @Test fun lowLevelTokenizationPositionsAndWhitespaceStrippingMatchOriginal() {
        val sources = listOf("a\r\nb\nc\u0085d\u2028e", "'a # b' '' #x\r\n\\u0041", "#tail", "#😀\n", "\\U0001f600 '😀'", "'a\nb'", "\\x{}", "\udc00", "a\u200eb\u200fc")
        for (rules in sources) {
            val js = get(builder(rules), "fScanner")!!
            val jc = get(js, "fC")!!
            val ks = RBBIRuleScanner(RBBIRuleParseResult(rules))
            repeat(rules.length + 2) {
                val je = runCatching { call(js, "nextChar", jc) }.exceptionOrNull()?.let { (it as java.lang.reflect.InvocationTargetException).cause!! }
                val ke = runCatching { ks.nextChar(ks.fC) }.exceptionOrNull()
                assertEquals(je?.message, ke?.message, rules)
                if (je != null) return@repeat
                assertEquals(get(jc, "fChar"), ks.fC.fChar, rules)
                assertEquals(get(jc, "fEscaped"), ks.fC.fEscaped, rules)
                for ((field, value) in mapOf("fScanIndex" to ks.fScanIndex, "fNextIndex" to ks.fNextIndex,
                    "fLineNum" to ks.fLineNum, "fCharNum" to ks.fCharNum)) assertEquals(get(js, field), value, rules)
            }
            assertEquals(call(js, "stripRules", rules), RBBIRuleScanner.stripRules(rules))
        }
    }
}
