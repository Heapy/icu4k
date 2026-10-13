package io.github.heapy.icu4k.text

import kotlin.random.Random
import kotlin.test.*

class RBBINodeOracleTest {
    private val type = Class.forName("com.ibm.icu.text.RBBINode")
    private fun get(node: Any, name: String): Any? = type.getDeclaredField(name).also { it.isAccessible = true }.get(node)
    private fun set(node: Any, name: String, value: Any?) = type.getDeclaredField(name).also { it.isAccessible = true }.set(node, value)
    private fun call(node: Any?, name: String, vararg args: Any?): Any? = type.declaredMethods.single { it.name == name && it.parameterCount == args.size }
        .also { it.isAccessible = true }.invoke(node, *args)
    private data class Nodes(val j: Any, val k: RBBINode)
    private var value = 0
    private fun node(kind: Int): Nodes {
        val j = type.getDeclaredConstructor(Int::class.javaPrimitiveType).also { it.isAccessible = true }.newInstance(kind)
        val k = RBBINode(kind)
        k.fSerialNum = get(j, "fSerialNum") as Int
        val v = ++value
        for ((field, item) in mapOf("fVal" to v, "fText" to "node$v", "fFirstPos" to v * 2, "fLastPos" to v * 2 + 1,
            "fNullable" to (v % 2 == 0), "fRuleRoot" to true, "fChainIn" to (v % 3 == 0), "fLookAheadEnd" to true)) set(j, field, item)
        k.fVal = v; k.fText = "node$v"; k.fFirstPos = v * 2; k.fLastPos = v * 2 + 1
        k.fNullable = v % 2 == 0; k.fRuleRoot = true; k.fChainIn = v % 3 == 0; k.fLookAheadEnd = true
        return Nodes(j, k)
    }
    private fun left(parent: Nodes, child: Nodes) {
        set(parent.j, "fLeftChild", child.j); set(child.j, "fParent", parent.j)
        parent.k.fLeftChild = child.k; child.k.fParent = parent.k
    }
    private fun right(parent: Nodes, child: Nodes) {
        set(parent.j, "fRightChild", child.j); set(child.j, "fParent", parent.j)
        parent.k.fRightChild = child.k; child.k.fParent = parent.k
    }
    private fun compare(j: Any?, k: RBBINode?) {
        if (j == null) { assertNull(k); return }
        assertNotNull(k)
        for ((name, expected) in mapOf("fType" to k.fType, "fPrecedence" to k.fPrecedence, "fText" to k.fText,
            "fFirstPos" to k.fFirstPos, "fLastPos" to k.fLastPos, "fNullable" to k.fNullable, "fVal" to k.fVal,
            "fLookAheadEnd" to k.fLookAheadEnd, "fRuleRoot" to k.fRuleRoot, "fChainIn" to k.fChainIn)) assertEquals(get(j, name), expected, name)
        assertEquals(get(j, "fParent")?.let { get(it, "fVal") }, k.fParent?.fVal)
        for ((name, expected) in listOf("fFirstPosSet" to k.fFirstPosSet, "fLastPosSet" to k.fLastPosSet, "fFollowPos" to k.fFollowPos)) {
            val original = get(j, name) as Set<*>
            assertEquals(original.map { get(it!!, "fVal") as Int }.sorted(), expected.map { it.fVal }.sorted())
        }
        compare(get(j, "fLeftChild"), k.fLeftChild); compare(get(j, "fRightChild"), k.fRightChild)
    }
    @Test fun copyingCloningVariablesAndSetsMatchOriginalTreeSemantics() {
        val random = Random(492105)
        fun tree(depth: Int): Nodes {
            val kind = if (depth == 0) RBBINode.leafChar else listOf(RBBINode.leafChar, RBBINode.tag, RBBINode.endMark,
                RBBINode.opCat, RBBINode.opOr, RBBINode.opStar, RBBINode.varRef).random(random)
            val root = node(kind)
            if (kind == RBBINode.opCat || kind == RBBINode.opOr) { left(root, tree(depth - 1)); right(root, tree(depth - 1)) }
            else if (kind == RBBINode.opStar || kind == RBBINode.varRef) left(root, tree(depth - 1))
            @Suppress("UNCHECKED_CAST") val first = get(root.j, "fFirstPosSet") as MutableSet<Any>
            first.add(root.j); root.k.fFirstPosSet.add(root.k)
            return root
        }
        for (kind in 0 until RBBINode.nodeTypeLimit) {
            val root = node(kind); compare(root.j, root.k)
            val copy = type.getDeclaredConstructor(type).also { it.isAccessible = true }.newInstance(root.j)
            val common = RBBINode(root.k); compare(copy, common)
            assertNull(common.fParent); assertFalse(common.fRuleRoot); assertFalse(common.fLookAheadEnd)
        }
        repeat(200) {
            val root = tree(5)
            compare(call(root.j, "cloneTree"), root.k.cloneTree())
            compare(call(root.j, "flattenVariables", 0), root.k.flattenVariables(0))
            for (kind in 0 until RBBINode.nodeTypeLimit) {
                val j = ArrayList<Any>(); val k = ArrayList<RBBINode>()
                call(root.j, "findNodes", j, kind); root.k.findNodes(k, kind)
                assertEquals(j.map { get(it, "fVal") }, k.map { it.fVal })
            }
        }
        val root = node(RBBINode.opCat); val uset = node(RBBINode.uset)
        val setRef1 = node(RBBINode.setRef); val setRef2 = node(RBBINode.setRef)
        val expression = node(RBBINode.opOr); left(expression, node(RBBINode.leafChar)); right(expression, node(RBBINode.leafChar))
        left(uset, expression); left(setRef1, uset); left(setRef2, uset); left(root, setRef1); right(root, setRef2)
        assertSame(uset.k, uset.k.cloneTree()); assertSame(uset.j, call(uset.j, "cloneTree"))
        call(root.j, "flattenSets"); root.k.flattenSets(); compare(root.j, root.k)
        assertNotSame(root.k.fLeftChild, root.k.fRightChild)
        assertSame(root.k, root.k.fLeftChild!!.fParent)
        val error = assertFailsWith<java.lang.reflect.InvocationTargetException> { call(root.j, "flattenVariables", 3501) }
        assertEquals(error.cause?.message, assertFailsWith<IllegalArgumentException> { root.k.flattenVariables(3501) }.message)
    }
    @Test fun debugFormattingMatchesOriginal() {
        fun capture(action: () -> Unit): String {
            val old = System.out; val bytes = java.io.ByteArrayOutputStream()
            try { System.setOut(java.io.PrintStream(bytes)); action() } finally { System.setOut(old) }
            return bytes.toString(Charsets.UTF_8)
        }
        for (width in listOf(-4, 0, 2, 12)) {
            assertEquals(capture { call(null, "printString", "abc", width) }, capture { RBBINode.printString("abc", width) })
            for (value in listOf(-123, 0, 123, Int.MAX_VALUE)) {
                assertEquals(capture { call(null, "printInt", value, width) }, capture { RBBINode.printInt(value, width) })
                assertEquals(capture { call(null, "printHex", value, width) }, capture { RBBINode.printHex(value, width) })
            }
        }
        val root = node(RBBINode.opCat); left(root, node(RBBINode.varRef)); right(root, node(RBBINode.leafChar))
        assertEquals(capture { call(root.j, "printTree", true) }, capture { root.k.printTree(true) })
        assertEquals(capture { call(null, "printNode", null) }, capture { RBBINode.printNode(null) })
    }
}
