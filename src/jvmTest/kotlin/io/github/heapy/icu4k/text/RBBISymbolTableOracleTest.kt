package io.github.heapy.icu4k.text

import kotlin.test.*

class RBBISymbolTableOracleTest {
    private val nodeClass = Class.forName("com.ibm.icu.text.RBBINode")
    private val symbolClass = Class.forName("com.ibm.icu.text.RBBISymbolTable")
    private fun oracle(): Any {
        val builderClass = Class.forName("com.ibm.icu.text.RBBIRuleBuilder")
        val builder = builderClass.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance("")
        val scanner = builderClass.getDeclaredField("fScanner").apply { isAccessible = true }.get(builder)
        return symbolClass.getDeclaredConstructor(scanner.javaClass).apply { isAccessible = true }.newInstance(scanner)
    }
    private fun call(target: Any, name: String, vararg args: Any): Any? =
        target.javaClass.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(target, *args)
    private fun node(type: Int, child: Any? = null, set: com.ibm.icu.text.UnicodeSet? = null): Any =
        nodeClass.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(type).also {
            nodeClass.getDeclaredField("fLeftChild").apply { isAccessible = true }.set(it, child)
            nodeClass.getDeclaredField("fInputSet").apply { isAccessible = true }.set(it, set)
        }

    @Test fun unicodeReferenceNamesAndLimitsMatchOriginal() {
        val original = oracle()
        val common = RBBISymbolTable { error("unexpected $it") }
        val names = listOf("", "1abc", "_abc", "abc def", "αβ2", "\uD801\uDC00x", "a\u200cb", "a-b", "\ud800x")
        for (text in names) for (start in 0..text.length) for (limit in start..text.length) {
            val jp = java.text.ParsePosition(start)
            val kp = ParsePosition(start)
            assertEquals(call(original, "parseReference", text, jp, limit), common.parseReference(text, kp, limit), "$text/$start/$limit")
            assertEquals(jp.index, kp.index)
        }
    }

    @Test fun variableChainsAndMatcherConsumptionMatchOriginal() {
        val original = oracle()
        val common = RBBISymbolTable { throw IllegalArgumentException("Error $it") }
        val js = com.ibm.icu.text.UnicodeSet("[a-z]")
        val ks = UnicodeSet("[a-z]")
        var j = node(RBBINode.varRef, node(RBBINode.setRef, node(RBBINode.uset, set = js)))
        var k = RBBINode(RBBINode.varRef).also { v ->
            v.fLeftChild = RBBINode(RBBINode.setRef).also { r ->
                r.fLeftChild = RBBINode(RBBINode.uset).also { it.fInputSet = ks }
            }
        }
        for (i in 0..5) {
            call(original, "addEntry", "v$i", j); common.addEntry("v$i", k)
            assertSame(j, call(original, "lookupNode", "v$i")); assertSame(k, common.lookupNode("v$i"))
            assertContentEquals(call(original, "lookup", "v$i") as CharArray, common.lookup("v$i"))
            assertNull(call(original, "lookup", "missing")); assertNull(common.lookup("missing"))
            assertNull(call(original, "lookupMatcher", 42)); assertNull(common.lookupMatcher(42))
            assertSame(js, call(original, "lookupMatcher", 0xffff)); assertSame(ks, common.lookupMatcher(0xffff))
            assertNull(call(original, "lookupMatcher", 0xffff)); assertNull(common.lookupMatcher(0xffff))
            val pattern = "[\$v$i 0]"
            assertEquals(com.ibm.icu.text.UnicodeSet(pattern, java.text.ParsePosition(0), original as com.ibm.icu.text.SymbolTable).toPattern(false),
                UnicodeSet(pattern, ParsePosition(0), common).toPattern(false))
            j = node(RBBINode.varRef, j)
            k = RBBINode(RBBINode.varRef).also { it.fLeftChild = k }
        }
        val jFailure = assertFailsWith<java.lang.reflect.InvocationTargetException> { call(original, "addEntry", "v0", j) }.cause!!
        val kFailure = assertFailsWith<IllegalArgumentException> { common.addEntry("v0", k) }
        assertTrue(jFailure.message!!.startsWith(kFailure.message!!))
        val jBad = node(RBBINode.varRef, node(RBBINode.opCat))
        val kBad = RBBINode(RBBINode.varRef).also { it.fLeftChild = RBBINode(RBBINode.opCat) }
        call(original, "addEntry", "bad", jBad); common.addEntry("bad", kBad)
        val malformed = assertFailsWith<java.lang.reflect.InvocationTargetException> { call(original, "lookup", "bad") }.cause!!
        assertTrue(malformed.message!!.startsWith(assertFailsWith<IllegalArgumentException> { common.lookup("bad") }.message!!))
    }
}
