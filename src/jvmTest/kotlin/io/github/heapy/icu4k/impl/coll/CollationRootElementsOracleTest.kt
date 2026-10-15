// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import kotlin.test.*
import com.ibm.icu.impl.coll.CollationRootElements as Source
import com.ibm.icu.impl.coll.CollationRoot as SourceRoot

class CollationRootElementsOracleTest {
    private val sourceMethods = Source::class.java.declaredMethods.filter { !Modifier.isPrivate(it.modifiers) && !it.isSynthetic }.onEach { it.isAccessible = true }.associateBy { it.name }
    private val targetMethods = sourceMethods.mapValues { (_, method) -> CollationRootElements::class.java.getDeclaredMethod(method.name, *method.parameterTypes).also { it.isAccessible = true } }
    private val getters = listOf("getTertiaryBoundary", "getFirstTertiaryCE", "getLastTertiaryCE", "getLastCommonSecondary", "getSecondaryBoundary", "getFirstSecondaryCE", "getLastSecondaryCE", "getFirstPrimary", "getFirstPrimaryCE")
    private fun invoke(source: Source, method: String, vararg args: Any): Any = try { sourceMethods.getValue(method).invoke(source, *args) } catch (e: InvocationTargetException) { throw e.targetException }
    private fun check(source: Source, target: CollationRootElements, method: String, vararg args: Any): Any {
        val expected = invoke(source, method, *args)
        val actual = try { targetMethods.getValue(method).invoke(target, *args) } catch (e: InvocationTargetException) { throw e.targetException }
        assertEquals(expected, actual, "$method/${args.toList()}")
        return expected
    }
    @Test fun everyEntryPointAcrossRealRootElementsAndRangeInteriors() {
        val data = SourceRoot.getData()
        val table = data.rootElements.copyOf()
        val snapshot = table.copyOf()
        val source = Source(table)
        val target = CollationRootElements(table)
        assertEquals(Source::class.java.desiredAssertionStatus(), CollationRootElements::class.java.desiredAssertionStatus())
        val constants = Source::class.java.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type in listOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType) }
        for (field in constants) {
            field.isAccessible = true
            val actual = CollationRootElements::class.java.getDeclaredField(field.name).also { it.isAccessible = true }
            assertEquals(field.get(null), actual.get(null), field.name)
        }
        for (getter in getters) check(source, target, getter)
        val called = getters.toMutableSet()
        fun compare(method: String, vararg args: Any): Any { called.add(method); return check(source, target, method, *args) }
        val first = table[2].toInt()
        val primaries = linkedSetOf<Long>()
        for (i in first until table.lastIndex) {
            val entry = table[i]
            if ((entry and 0x80) != 0L) continue
            val p = entry and 0xffffff00L
            // The table includes the unassigned boundary, which findP explicitly forbids as input.
            if ((p shr 24) == Collation.UNASSIGNED_IMPLICIT_BYTE.toLong()) continue
            primaries.add(p)
            if ((entry and 0x7f) != 0L) {
                val start = table[i - 1] and 0xffffff00L
                val step = entry.toInt() and 0x7f
                val inside = if ((start and 0xffff) == 0L) com.ibm.icu.impl.coll.Collation.incTwoBytePrimaryByOffset(start, data.isCompressiblePrimary(start), step)
                    else com.ibm.icu.impl.coll.Collation.incThreeBytePrimaryByOffset(start, data.isCompressiblePrimary(start), step)
                if (inside < p) primaries.add(inside)
                val beforeEnd = invoke(source, "getPrimaryBefore", p, data.isCompressiblePrimary(p)) as Long
                if (beforeEnd > start) primaries.add(beforeEnd)
            }
        }
        for (p in primaries) {
            val index = compare("findPrimary", p) as Int
            compare("getPrimaryAfter", p, index, data.isCompressiblePrimary(p))
            if (p > table[first]) compare("getPrimaryBefore", p, data.isCompressiblePrimary(p))
            if ((table[index] and 0x7f) == 0L && p == (table[index] and 0xffffff00L)) {
                compare("firstCEWithPrimaryAtLeast", p)
                if (p > table[first]) compare("lastCEWithPrimaryBefore", p)
            }
        }
        var primary = 0L
        val tuples = mutableListOf<Triple<Long, Int, Int>>()
        for (i in table[0].toInt() until table.lastIndex) {
            val entry = table[i]
            if ((entry and 0x80) != 0L) {
                val st = entry and 0x80L.inv()
                tuples.add(Triple(primary, (st ushr 16).toInt(), st.toInt() and 0xffff))
            } else {
                primary = entry and 0xffffff00L
                val next = table[i + 1]
                if ((next and 0x80) == 0L || (next and 0x80L.inv()) > 0x05000500L) tuples.add(Triple(primary, 0x0500, 0x0500))
            }
        }
        for ((p, s, t) in tuples) {
            if ((p shr 24) == Collation.UNASSIGNED_IMPLICIT_BYTE.toLong()) continue
            val index = if (p == 0L) 0 else invoke(source, "findPrimary", p) as Int
            if (p != 0L || s != 0) {
                val before = compare("getSecondaryBefore", p, s) as Int
                compare("getSecondaryAfter", index, s)
                if (p != 0L || before != 0) compare("getSecondaryAfter", index, before)
            }
            val before = compare("getTertiaryBefore", p, s, t) as Int
            compare("getTertiaryAfter", index, s, t)
            if (s != 0 || before != 0) compare("getTertiaryAfter", index, s, before)
        }
        sameOutcome(source, target, "findPrimary", 0xfe040200L)
        compare("firstCEWithPrimaryAtLeast", 0L)
        compare("lastCEWithPrimaryBefore", 0L)
        assertEquals(sourceMethods.keys, called)
        assertContentEquals(snapshot, table)
    }
    @Test fun syntheticDeltaSearchAliasingAndFailureOrder() {
        val table = rootElementsFixture()
        val source = Source(table); val target = CollationRootElements(table)
        for (getter in getters) check(source, target, getter)
        val ps = listOf(0L, 0x11040000L, 0x12030000L, 0x12040000L, 0x12050000L, 0x12070000L, 0x13040200L, 0x13040400L, 0x14040000L, 0xfe000000L, Source.PRIMARY_SENTINEL, -1L, Long.MIN_VALUE)
        for (p in ps) {
            for (name in listOf("findPrimary", "firstCEWithPrimaryAtLeast", "lastCEWithPrimaryBefore")) sameOutcome(source, target, name, p)
            for (c in listOf(false, true)) sameOutcome(source, target, "getPrimaryBefore", p, c)
            for (s in listOf(Int.MIN_VALUE, -1, 0, 0x100, 0x400, 0x500, 0x600, 0x8200, 0x8400, Int.MAX_VALUE)) {
                sameOutcome(source, target, "getSecondaryBefore", p, s)
                for (t in listOf(-1, 0, 0x100, 0x400, 0x500, 0x600, 0x3d02, 0x4000)) sameOutcome(source, target, "getTertiaryBefore", p, s, t)
            }
        }
        for (index in listOf(-1, 0, 1, 10, 15, 16, 19, table.lastIndex, table.size)) {
            for (s in listOf(Int.MIN_VALUE, -1, 0, 0x100, 0x500, 0x600, 0x8200, Int.MAX_VALUE)) {
                sameOutcome(source, target, "getSecondaryAfter", index, s)
                for (t in listOf(-1, 0, 0x100, 0x500, 0x600, 0x3d02)) sameOutcome(source, target, "getTertiaryAfter", index, s, t)
            }
            sameOutcome(source, target, "getPrimaryAfter", 0x12040000L, index, true)
        }
        table[4] = 0x22550011; table[0] = 6
        for (getter in getters) check(source, target, getter)
        for (bad in listOf<LongArray?>(null, longArrayOf(), longArrayOf(0), longArrayOf(0, -1, Int.MAX_VALUE.toLong()))) {
            val s = Source(bad); val t = CollationRootElements(bad)
            for (getter in getters) sameOutcome(s, t, getter)
            sameOutcome(s, t, "firstCEWithPrimaryAtLeast", 0L)
            sameOutcome(s, t, "lastCEWithPrimaryBefore", 0L)
            sameOutcome(s, t, "getTertiaryBefore", 0L, 0, 0x4000)
        }
    }
    @Test fun disabledAssertionsSkipArrayReadsInIsolatedClasses() {
        val urls = arrayOf(Source::class.java.protectionDomain.codeSource.location, CollationRootElements::class.java.protectionDomain.codeSource.location, Unit::class.java.protectionDomain.codeSource.location)
        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            loader.setDefaultAssertionStatus(false)
            val sClass = loader.loadClass(Source::class.java.name)
            val tClass = loader.loadClass(CollationRootElements::class.java.name)
            assertFalse(sClass.desiredAssertionStatus()); assertFalse(tClass.desiredAssertionStatus())
            val source = sClass.getConstructor(LongArray::class.java).newInstance(longArrayOf(0))
            val target = tClass.getConstructor(LongArray::class.java).newInstance(longArrayOf(0))
            val signature = arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            val sm = sClass.getDeclaredMethod("getPrimaryAfter", *signature).also { it.isAccessible = true }
            val tm = tClass.getDeclaredMethod("getPrimaryAfter", *signature).also { it.isAccessible = true }
            assertEquals(0L, sm.invoke(source, 0L, -1, false))
            assertEquals(0L, tm.invoke(target, 0L, -1, false))
            // Assertion policy changes after initialization must not activate precondition reads.
            loader.setClassAssertionStatus(sClass.name, true); loader.setClassAssertionStatus(tClass.name, true)
            assertEquals(0L, sm.invoke(source, 0L, -1, false))
            assertEquals(0L, tm.invoke(target, 0L, -1, false))
        }
    }
    private fun sameOutcome(source: Source, target: CollationRootElements, name: String, vararg args: Any) {
        fun outcome(block: () -> Any?): Any? = try { block() } catch (error: Throwable) {
            val cause = if (error is InvocationTargetException) error.targetException else error
            when (cause) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> "${cause.javaClass.simpleName}:${cause.message}" }
        }
        assertEquals(outcome { sourceMethods.getValue(name).invoke(source, *args) }, outcome { targetMethods.getValue(name).invoke(target, *args) }, "$name/${args.toList()}")
    }
}
