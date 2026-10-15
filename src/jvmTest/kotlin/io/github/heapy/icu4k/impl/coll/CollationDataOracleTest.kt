// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.Trie2_32
import io.github.heapy.icu4k.text.UnicodeSet
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.CollationData as Source
import com.ibm.icu.impl.coll.CollationRoot as SourceRoot
import com.ibm.icu.impl.coll.UVector32 as SourceVector

class CollationDataOracleTest {
    private val sourceFields = Source::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.onEach { it.isAccessible = true }
    private val methods = Source::class.java.declaredMethods.onEach { it.isAccessible = true }.associateBy { it.name to it.parameterCount }
    private fun invoke(source: Source, name: String, vararg args: Any?): Any? = try { methods.getValue(name to args.size).invoke(source, *args) } catch (e: InvocationTargetException) { throw e.targetException }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "NullPointerException"
            is IndexOutOfBoundsException -> "IndexOutOfBoundsException"
            else -> "${e.javaClass.simpleName}:${e.message}"
        }
    }
    private fun compare(source: Source, target: CollationData, name: String, vararg args: Any?, actual: () -> Any?) {
        assertEquals(outcome { invoke(source, name, *args) }, outcome(actual), "$name/${args.toList()}")
    }
    @Test fun realRootTrieElementsNormalizationAndSafety() {
        val source = SourceRoot.getData()
        val target = importSource(source)
        val random = Random(7803)
        val points = listOf(Int.MIN_VALUE, -1, 0, 1, 48, 65, 0xdf, 0x301, 0x660, 0xd800, 0xdc00, 0xffff, 0x10000, 0x10ffff, 0x110000, Int.MAX_VALUE) + List(4000) { random.nextInt(0x110000) }
        for (c in points) {
            compare(source, target, "getCE32", c) { target.getCE32(c) }
            compare(source, target, "getCE32FromSupplementary", c) { target.getCE32FromSupplementary(c) }
            compare(source, target, "isDigit", c) { target.isDigit(c) }
            compare(source, target, "getSingleCE", c) { target.getSingleCE(c) }
            compare(source, target, "getFCD16", c) { target.getFCD16(c) }
            for (numeric in listOf(false, true)) compare(source, target, "isUnsafeBackward", c, numeric) { target.isUnsafeBackward(c, numeric) }
        }
        for (b in -1..256) compare(source, target, "isCompressibleLeadByte", b) { target.isCompressibleLeadByte(b) }
        repeat(1000) {
            val p = random.nextLong()
            compare(source, target, "isCompressiblePrimary", p) { target.isCompressiblePrimary(p) }
        }
        val contexts = target.contexts!!
        for (index in listOf(-1, 0, 1, contexts.length - 2, contexts.length - 1, contexts.length))
            compare(source, target, "getCE32FromContexts", index) { target.getCE32FromContexts(index) }
    }
    @Test fun rootScriptGroupsAliasesAndReordering() {
        val source = SourceRoot.getData()
        val target = importSource(source)
        for (script in (-2..target.numScripts + 2).toList() + (4096..4113).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            compare(source, target, "getFirstPrimaryForGroup", script) { target.getFirstPrimaryForGroup(script) }
            compare(source, target, "getLastPrimaryForGroup", script) { target.getLastPrimaryForGroup(script) }
            assertContentEquals(invoke(source, "getEquivalentScripts", script) as IntArray, target.getEquivalentScripts(script), "equivalent/$script")
        }
        for (start in target.scriptStarts!!) for (delta in listOf(-1L, 0L, 1L)) {
            val primary = (start.code.toLong() shl 16) + delta
            compare(source, target, "getGroupForPrimary", primary) { target.getGroupForPrimary(primary) }
        }
        val cases = mutableListOf<IntArray?>(null, intArrayOf(), intArrayOf(103), intArrayOf(-1), intArrayOf(25), intArrayOf(14, 25), intArrayOf(103, 25), intArrayOf(25, 103, 14), intArrayOf(4096, 25, 103, 4097), intArrayOf(25, 25), intArrayOf(20, 22), intArrayOf(103, 103), intArrayOf(25, -1))
        val random = Random(78031)
        val codes = (0 until target.numScripts).toList() + (4096..4103).toList() + listOf(-1, -2, 103, Int.MAX_VALUE)
        repeat(1000) { cases.add(IntArray(random.nextInt(0, 12)) { codes.random(random) }) }
        val seenGroups = mutableSetOf<Int>()
        val canonical = (0 until target.numScripts).filter { val index = target.scriptsIndex!![it].code; index != 0 && seenGroups.add(index) }
        repeat(100) { cases.add(canonical.shuffled(random).toIntArray()) }
        for (reorder in cases) compareReorder(source, target, reorder)
    }
    @Test fun syntheticTagsFallbackOwnershipErrorsAndDefaults() {
        val fieldNames = sourceFields.map { it.name }.sorted()
        assertEquals(fieldNames, CollationData::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map { it.name }.sorted())
        val target = CollationData(null)
        var source = exportTarget(target)
        assertEquals(0x12000000L, target.numericPrimary)
        assertContentEquals(source.jamoField(), target.jamoCE32s)
        compare(source, target, "getCE32", 65) { target.getCE32(65) }
        val sEmpty = invoke(source, "getEquivalentScripts", -1)
        assertSame(sEmpty, invoke(source, "getEquivalentScripts", Int.MIN_VALUE))
        assertSame(target.getEquivalentScripts(-1), CollationData(null).getEquivalentScripts(-2))
        val simple = 0x12340505
        val points = listOf(0, 65, 0x660, 0xffff, 0x10ffff, -1, Int.MIN_VALUE)
        for (tag in 0..15) for (length in listOf(0, 1, 2, 31)) {
            target.trie = constantCollationTrie(Collation.makeCE32FromTagIndexAndLength(tag, 1, length))
            target.ce32s = intArrayOf(simple, simple)
            target.ces = longArrayOf(0, 0x1234567805000500L)
            target.base = CollationData(null).also { it.trie = constantCollationTrie(simple) }
            source = exportTarget(target)
            for (c in points) compare(source, target, "getSingleCE", c) { target.getSingleCE(c) }
            val ce32 = target.getCE32(0)
            compare(source, target, "getIndirectCE32", ce32) { target.getIndirectCE32(ce32) }
            compare(source, target, "getFinalCE32", ce32) { target.getFinalCE32(ce32) }
            compare(source, target, "getCEFromOffsetCE32", 65, ce32) { target.getCEFromOffsetCE32(65, ce32) }
        }
        val parent = CollationData(null).also { it.trie = constantCollationTrie(Collation.makeCE32FromTagIndexAndLength(5, 1, 1)); it.ce32s = intArrayOf(0, simple) }
        target.trie = constantCollationTrie(Collation.FALLBACK_CE32); target.base = parent
        target.ce32s = intArrayOf(0, 0x23450505)
        source = exportTarget(target)
        compare(source, target, "getSingleCE", 65) { target.getSingleCE(65) }
        parent.trie = constantCollationTrie(Collation.FALLBACK_CE32)
        source = exportTarget(target)
        compare(source, target, "getSingleCE", 65) { target.getSingleCE(65) }
        for (codes in listOf(null, intArrayOf(), intArrayOf(103), intArrayOf(25))) compareReorder(exportTarget(CollationData(null)), CollationData(null), codes)
        val scripted = scriptCollationData()
        for (codes in listOf(intArrayOf(14, 7), intArrayOf(25, 25), intArrayOf(103, 103), intArrayOf(4096, 4096))) compareReorder(exportTarget(scripted), scripted, codes)
        val retry = scriptCollationData().also { it.scriptStarts!![4] = 0x3001.toChar() }
        compareReorder(exportTarget(retry), retry, intArrayOf(25, 8, 14))
        val overflow = partialCollationData()
        compareReorder(exportTarget(overflow), overflow, intArrayOf(25, 8, 14))

    }
    private fun compareReorder(source: Source, target: CollationData, reorder: IntArray?) {
        val s = SourceVector().also { it.addElement(99) }
        val t = UVector32().also { it.addElement(99) }
        assertEquals(outcome { invoke(source, "makeReorderRanges", reorder, s); "ok" }, outcome { target.makeReorderRanges(reorder, t); "ok" }, "reorder/${reorder?.toList()}")
        assertEquals(s.size(), t.size())
        assertContentEquals(s.getBuffer(), t.getBuffer(), "range buffer/${reorder?.toList()}")
    }
    internal fun importSource(source: Source, seen: IdentityHashMap<Source, CollationData> = IdentityHashMap()): CollationData {
        seen[source]?.let { return it }
        val target = CollationData(if (source.nfcImpl == null) null else Norm2AllModes.getNFCInstance().impl)
        seen[source] = target
        for (field in sourceFields) {
            val raw = field.get(source)
            val value = when (field.name) {
                "nfcImpl" -> target.nfcImpl
                "base" -> (raw as Source?)?.let { importSource(it, seen) }
                "trie" -> (raw as com.ibm.icu.impl.Trie2_32?)?.let { val bytes = ByteArrayOutputStream(); it.serialize(bytes); Trie2_32.createFromSerialized(bytes.toByteArray()) }
                "unsafeBackwardSet" -> (raw as com.ibm.icu.text.UnicodeSet?)?.let { set -> UnicodeSet().also { dest -> for (i in 0 until set.rangeCount) dest.add(set.getRangeStart(i), set.getRangeEnd(i)) } }
                else -> raw
            }
            CollationData::class.java.getDeclaredField(field.name).also { it.isAccessible = true }.set(target, value)
        }
        return target
    }
    internal fun exportTarget(target: CollationData, seen: IdentityHashMap<CollationData, Source> = IdentityHashMap()): Source {
        seen[target]?.let { return it }
        val source = Source::class.java.getDeclaredConstructor(com.ibm.icu.impl.Normalizer2Impl::class.java).also { it.isAccessible = true }.newInstance(null)
        seen[target] = source
        for (field in sourceFields) {
            val raw = CollationData::class.java.getDeclaredField(field.name).also { it.isAccessible = true }.get(target)
            val value = when (field.name) {
                "nfcImpl" -> if (raw == null) null else SourceRoot.getData().nfcImpl
                "base" -> (raw as CollationData?)?.let { exportTarget(it, seen) }
                "trie" -> (raw as Trie2_32?)?.let { com.ibm.icu.impl.Trie2_32.createFromSerialized(ByteBuffer.wrap(it.serialize())) }
                "unsafeBackwardSet" -> (raw as UnicodeSet?)?.let { com.ibm.icu.text.UnicodeSet(it.toPattern(false)) }
                else -> raw
            }
            field.set(source, value)
        }
        return source
    }
    private fun Source.jamoField(): IntArray = Source::class.java.getDeclaredField("jamoCE32s").also { it.isAccessible = true }.get(this) as IntArray
}
