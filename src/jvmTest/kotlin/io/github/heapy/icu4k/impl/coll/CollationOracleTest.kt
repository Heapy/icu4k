// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.Collation as Source

class CollationOracleTest {
    @Test fun everySourceConstantAndHelperMatches() {
        val sourceClass = Source::class.java
        val targetClass = Collation::class.java
        assertEquals(sourceClass.desiredAssertionStatus(), targetClass.desiredAssertionStatus())
        val sourceFields = sourceClass.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type in listOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType) }
        val targetFields = targetClass.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type in listOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType) }
        assertEquals(sourceFields.map { it.name }.sorted(), targetFields.map { it.name }.sorted())
        for (field in sourceFields) {
            field.isAccessible = true
            val actual = targetClass.getDeclaredField(field.name).also { it.isAccessible = true }
            assertEquals(field.get(null), actual.get(null), field.name)
        }
        val random = Random(7803)
        val integerEdges = listOf(Int.MIN_VALUE, Int.MAX_VALUE, -1, 0, 1, 2, 4, 15, 31, 127, 128, 192, 255, 0xffff, 0x10ffff, 0x7ffff)
        val primaryEdges = listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L, 0x12020200L, 0x12040200L, 0x12feff00L, 0x12ffff00L, 0xff000000L)
        val methods = sourceClass.declaredMethods.filter { Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        assertEquals(33, methods.size)
        for (method in methods) {
            method.isAccessible = true
            val actual = targetClass.getDeclaredMethod(method.name, *method.parameterTypes).also { it.isAccessible = true }
            fun check(vararg args: Any) {
                assertEquals(result(method, null, args), result(actual, Collation, args), "${method.name}/${args.toList()}")
            }
            when (method.parameterTypes.toList()) {
                listOf(Int::class.javaPrimitiveType) -> {
                    for (i in integerEdges + (0..255).toList() + List(1500) { random.nextInt() }) check(i)
                }
                listOf(Long::class.javaPrimitiveType) -> {
                    for (p in primaryEdges + List(1500) { random.nextLong() }) check(p)
                }
                listOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) -> {
                    for (a in integerEdges) for (b in integerEdges) check(a, b)
                    repeat(1000) { check(random.nextInt(), random.nextInt()) }
                }
                listOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) -> {
                    for (a in 0..15) for (b in integerEdges) for (c in listOf(0, 1, 31, 32, -1)) check(a, b, c)
                    repeat(1000) { check(random.nextInt(), random.nextInt(), random.nextInt()) }
                }
                listOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) -> {
                    for (p in primaryEdges) for (v in integerEdges) check(p, v, v, v)
                    repeat(1500) { check(random.nextLong(), random.nextInt(), random.nextInt(), random.nextInt()) }
                }
                listOf(Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType) -> {
                    for (p in primaryEdges) for (compress in listOf(false, true)) for (delta in integerEdges) check(p, compress, delta)
                    repeat(1500) { check(random.nextLong(), random.nextBoolean(), if (method.name.startsWith("dec")) random.nextInt(1, 128) else random.nextInt()) }
                }
                listOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType) -> {
                    for (c in integerEdges) for (data in primaryEdges) check(c, data)
                    repeat(1500) { check(random.nextInt(), random.nextLong()) }
                }
                else -> fail("Uncovered source signature: $method")
            }
        }
    }
    @Test fun assertionsAreFixedAtClassInitialization() {
        val sourceClass = Source::class.java
        val targetClass = Collation::class.java
        val sourceStatus = sourceClass.desiredAssertionStatus()
        val targetStatus = targetClass.desiredAssertionStatus()
        val source = sourceClass.getDeclaredMethod("ceFromSimpleCE32", Int::class.javaPrimitiveType).also { it.isAccessible = true }
        val target = targetClass.getDeclaredMethod("ceFromSimpleCE32", Int::class.javaPrimitiveType).also { it.isAccessible = true }
        // Initialize both classes, including their assertion snapshots, before changing loader policy.
        val before = result(source, null, arrayOf(0xc0))
        assertEquals(before, result(target, Collation, arrayOf(0xc0)))
        try {
            sourceClass.classLoader.setClassAssertionStatus(sourceClass.name, !sourceStatus)
            targetClass.classLoader.setClassAssertionStatus(targetClass.name, !targetStatus)
            assertEquals(!sourceStatus, sourceClass.desiredAssertionStatus())
            assertEquals(!targetStatus, targetClass.desiredAssertionStatus())
            assertEquals(before, result(source, null, arrayOf(0xc0)))
            assertEquals(before, result(target, Collation, arrayOf(0xc0)))
        } finally {
            sourceClass.classLoader.setClassAssertionStatus(sourceClass.name, sourceStatus)
            targetClass.classLoader.setClassAssertionStatus(targetClass.name, targetStatus)
        }
    }
    private fun result(method: Method, owner: Any?, args: Array<out Any>): Any? = try { method.invoke(owner, *args) } catch (e: InvocationTargetException) {
        val cause = e.targetException
        "${cause.javaClass.name}:${cause.message}"
    }
}
