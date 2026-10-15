// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.math

import com.ibm.icu.math.BigDecimal as JDecimal
import com.ibm.icu.math.MathContext as JContext
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.*

class BigDecimalOracleTest {
    private val jf = listOf("ind", "form", "mant", "exp").associateWith { JDecimal::class.java.getDeclaredField(it).also { f -> f.isAccessible = true } }
    private val kf = listOf("ind", "form", "mant", "exp").associateWith { BigDecimal::class.java.getDeclaredField(it).also { f -> f.isAccessible = true } }
    private fun state(j: JDecimal, k: BigDecimal, label: String = "") {
        for (name in listOf("ind", "form", "exp")) assertEquals(jf.getValue(name).get(j), kf.getValue(name).get(k), "$label/$name")
        assertContentEquals(jf.getValue("mant").get(j) as ByteArray?, kf.getValue("mant").get(k) as ByteArray?, "$label/mant")
        val exp = jf.getValue("exp").getInt(j)
        if (jf.getValue("mant").get(j) != null && kotlin.math.abs(exp.toLong()) < 2000) assertEquals(j.toString(), k.toString(), label)
    }
    private fun result(j: Result<Any?>, k: Result<Any?>, jl: JDecimal?, kl: BigDecimal?, jr: JDecimal? = null, kr: BigDecimal? = null, label: String = "") {
        assertEquals(j.exceptionOrNull()?.javaClass, k.exceptionOrNull()?.javaClass, label)
        if (j.exceptionOrNull() !is NullPointerException && j.exceptionOrNull() !is IndexOutOfBoundsException) assertEquals(j.exceptionOrNull()?.message, k.exceptionOrNull()?.message, label)
        val a = j.getOrNull(); val b = k.getOrNull()
        if (a is JDecimal) {
            state(a, b as BigDecimal, label)
            assertEquals(a === jl, b === kl, "$label/left identity")
            assertEquals(a === jr, b === kr, "$label/right identity")
            for ((jc, kc) in listOf(JDecimal.ZERO to BigDecimal.ZERO, JDecimal.ONE to BigDecimal.ONE, JDecimal.TEN to BigDecimal.TEN)) {
                assertEquals(a === jc, b === kc, "$label/constant identity")
                assertEquals(jf.getValue("mant").get(a) === jf.getValue("mant").get(jc), kf.getValue("mant").get(b) === kf.getValue("mant").get(kc), "$label/constant mantissa")
            }
            if (jl != null) assertEquals(jf.getValue("mant").get(a) === jf.getValue("mant").get(jl), kf.getValue("mant").get(b) === kf.getValue("mant").get(kl), "$label/left mantissa")
            if (jr != null) assertEquals(jf.getValue("mant").get(a) === jf.getValue("mant").get(jr), kf.getValue("mant").get(b) === kf.getValue("mant").get(kr), "$label/right mantissa")
        } else if (a is CharArray) assertContentEquals(a, b as CharArray)
        else assertEquals(a, b, label)
    }
    private fun portableType(type: Class<*>): Class<*> = when (type) { JDecimal::class.java -> BigDecimal::class.java; JContext::class.java -> MathContext::class.java; else -> type }
    @Test fun completeSourcePublicApiIsPresent() {
        for (method in JDecimal::class.java.declaredMethods.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }) {
            val mapped = BigDecimal::class.java.getDeclaredMethod(method.name, *method.parameterTypes.map(::portableType).toTypedArray())
            assertEquals(portableType(method.returnType), mapped.returnType, method.toString())
            assertEquals(Modifier.isStatic(method.modifiers), Modifier.isStatic(mapped.modifiers), method.toString())
        }
        for (constructor in JDecimal::class.java.constructors) BigDecimal::class.java.getConstructor(*constructor.parameterTypes)
        for (field in JDecimal::class.java.fields.filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }) {
            assertEquals(field.getInt(null), BigDecimal::class.java.getField(field.name).getInt(null))
        }
    }
    @Test fun constructorsFactoriesAndVirtualIntegerText() {
        val random = Random(67381)
        val inputs = listOf("0", "0.000", "1E+20", "1.20", "-18446744073709551617.999", "١٢٣.٤٥") + List(90) { "${random.nextLong()}E${random.nextInt(-30, 30)}" }
        for (text in inputs) {
            val j = JDecimal(text); val k = BigDecimal(text)
            state(j, k, text)
            state(JDecimal(text.toCharArray()), BigDecimal(text.toCharArray()), text)
            state(JDecimal(("x"+text+"y").toCharArray(), 1, text.length), BigDecimal(("x"+text+"y").toCharArray(), 1, text.length), text)
            val standard = java.math.BigDecimal(text)
            state(JDecimal(standard), BigDecimal(standard), text)
        }
        for (value in listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0, 1, -1, 10) + List(100) { random.nextLong() }) {
            result(runCatching { JDecimal(value) }, runCatching { BigDecimal(value) }, null, null, label = "long $value")
            result(runCatching { JDecimal(value.toInt()) }, runCatching { BigDecimal(value.toInt()) }, null, null, label = "int $value")
            for (scale in listOf(-1, 0, 1, 9)) result(runCatching { JDecimal.valueOf(value, scale) }, runCatching { BigDecimal.valueOf(value, scale) }, null, null, label = "factory $value/$scale")
            val integer = BigInteger(value.toString()).shiftLeft(90)
            state(JDecimal(integer), BigDecimal(integer))
            for (scale in listOf(-1, 0, 3)) result(runCatching { JDecimal(integer, scale) }, runCatching { BigDecimal(integer, scale) }, null, null)
        }
        for (value in listOf(-0.0, 0.1, 1e-5, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY) + List(70) { Double.fromBits(random.nextLong()) }) {
            result(runCatching { JDecimal(value) }, runCatching { BigDecimal(value) }, null, null, label = "double $value")
            result(runCatching { JDecimal.valueOf(value) }, runCatching { BigDecimal.valueOf(value) }, null, null, label = "factory $value")
        }
        class IntegerText(val text: String, val calls: MutableList<Int>) : BigInteger("0") {
            override fun toString(radix: Int): String { calls.add(radix); return text }
            override fun toByte(): Byte = toInt().toByte()
            override fun toShort(): Short = toInt().toShort()
        }
        for (text in listOf("1.20", "1E+3", "not a number")) for (scale in listOf(-1, 0, 3)) {
            val jCalls = arrayListOf<Int>(); val kCalls = arrayListOf<Int>()
            result(runCatching { JDecimal(IntegerText(text, jCalls), scale) }, runCatching { BigDecimal(IntegerText(text, kCalls), scale) }, null, null)
            assertEquals(jCalls, kCalls)
            result(runCatching { JDecimal(IntegerText(text, jCalls)) }, runCatching { BigDecimal(IntegerText(text, kCalls)) }, null, null)
            assertEquals(jCalls, kCalls)
        }
    }
    @Test fun publicOperationsPreserveStateIdentityAndExceptions() {
        val random = Random(67382)
        val values = listOf("0", "0.000", "1", "-1", "10", "1.20", "-1.200", "999.95", "1E+20", "1E-20", "-18446744073709551617.999")
        repeat(250) { i ->
            val lt = values[random.nextInt(values.size)]; val rt = values[random.nextInt(values.size)]
            val jl = JDecimal(lt); val kl = BigDecimal(lt)
            val jr = if (i % 7 == 0) jl else JDecimal(rt); val kr = if (i % 7 == 0) kl else BigDecimal(rt)
            val digits = random.nextInt(10); val form = random.nextInt(3); val lost = random.nextBoolean(); val round = random.nextInt(8)
            val jc = JContext(digits, form, lost, round); val kc = MathContext(digits, form, lost, round)
            val operations = listOf("add", "subtract", "multiply", "divide", "divideInteger", "remainder", "max", "min", "compareTo")
            for (name in operations) {
                val jm = JDecimal::class.java.getMethod(name, JDecimal::class.java, JContext::class.java)
                val km = BigDecimal::class.java.getMethod(name, BigDecimal::class.java, MathContext::class.java)
                fun invoke(method: java.lang.reflect.Method, receiver: Any, vararg args: Any?): Any? = try { method.invoke(receiver, *args) } catch (e: InvocationTargetException) { throw e.targetException }
                result(runCatching { invoke(jm, jl, jr, jc) }, runCatching { invoke(km, kl, kr, kc) }, jl, kl, jr, kr, "$i/$name")
            }
            val jOps: List<() -> Any?> = listOf({ jl.plus(jc) }, { jl.negate(jc) }, { jl.abs(jc) }, { jl.plus() }, { jl.negate() }, { jl.abs() }, { jl.divide(jr, round) }, { jl.divide(jr, 4, round) }, { jl.setScale(3, round) }, { jl.movePointLeft(2) }, { jl.movePointRight(3) }, { jl.pow(JDecimal("-3"), jc) }, { jl.pow(JDecimal("3")) })
            val kOps: List<() -> Any?> = listOf({ kl.plus(kc) }, { kl.negate(kc) }, { kl.abs(kc) }, { kl.plus() }, { kl.negate() }, { kl.abs() }, { kl.divide(kr, round) }, { kl.divide(kr, 4, round) }, { kl.setScale(3, round) }, { kl.movePointLeft(2) }, { kl.movePointRight(3) }, { kl.pow(BigDecimal("-3"), kc) }, { kl.pow(BigDecimal("3")) })
            for (op in jOps.indices) result(runCatching(jOps[op]), runCatching(kOps[op]), jl, kl, jr, kr, "$i/unary/$op")
            val jConversions: List<() -> Any?> = listOf({ jl.toBigDecimal() }, { jl.toBigInteger() }, { jl.toBigIntegerExact() }, { jl.unscaledValue() }, { jl.toCharArray() }, { jl.toByte() }, { jl.toShort() }, { jl.toInt() }, { jl.toLong() }, { jl.toFloat() }, { jl.toDouble() }, { jl.byteValueExact() }, { jl.shortValueExact() }, { jl.intValueExact() }, { jl.longValueExact() }, { jl.hashCode() }, { jl.scale() }, { jl.signum() }, { jl.format(8, 2) }, { jl.format(8, 2, 3, 6, form, round) }, { jl == jr })
            val kConversions: List<() -> Any?> = listOf({ kl.toBigDecimal() }, { kl.toBigInteger() }, { kl.toBigIntegerExact() }, { kl.unscaledValue() }, { kl.toCharArray() }, { kl.toByte() }, { kl.toShort() }, { kl.toInt() }, { kl.toLong() }, { kl.toFloat() }, { kl.toDouble() }, { kl.byteValueExact() }, { kl.shortValueExact() }, { kl.intValueExact() }, { kl.longValueExact() }, { kl.hashCode() }, { kl.scale() }, { kl.signum() }, { kl.format(8, 2) }, { kl.format(8, 2, 3, 6, form, round) }, { kl == kr })
            for (op in jConversions.indices) result(runCatching(jConversions[op]), runCatching(kConversions[op]), jl, kl, jr, kr, "$i/conversion/$op")
            state(jl, kl); state(jr, kr)
        }
    }
    private fun stream(value: Any, from: Class<*>, to: Class<*>): Any {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(value) } }.toByteArray()
        return object : ObjectInputStream(ByteArrayInputStream(bytes)) {
            override fun readClassDescriptor(): ObjectStreamClass {
                val desc = super.readClassDescriptor()
                return if (desc.name == from.name) ObjectStreamClass.lookup(to) else desc
            }
        }.use { it.readObject() }
    }
    @Test fun serializationShapeInterchangeAndMalformedState() {
        val jd = ObjectStreamClass.lookup(JDecimal::class.java); val kd = ObjectStreamClass.lookup(BigDecimal::class.java)
        assertEquals(jd.serialVersionUID, kd.serialVersionUID)
        assertEquals(jd.fields.map { it.name to it.type }, kd.fields.map { it.name to it.type })
        assertEquals<Class<*>>(JDecimal::class.java.superclass, BigDecimal::class.java.superclass)
        for (text in listOf("0", "0.000", "1.2300", "-99E+20")) {
            val j = JDecimal(text); val k = BigDecimal(text)
            state(j, stream(j, JDecimal::class.java, BigDecimal::class.java) as BigDecimal)
            state(stream(k, BigDecimal::class.java, JDecimal::class.java) as JDecimal, k)
        }
        val j = JDecimal("1.20"); val k = BigDecimal("1.20")
        jf.getValue("mant").set(j, null); kf.getValue("mant").set(k, null)
        val jr = stream(j, JDecimal::class.java, JDecimal::class.java) as JDecimal
        val kr = stream(k, BigDecimal::class.java, BigDecimal::class.java) as BigDecimal
        result(runCatching { jr.plus() }, runCatching { kr.plus() }, jr, kr) // Unlimited plain plus does not touch mantissa.
        assertEquals(jr.signum(), kr.signum()); assertEquals(jr.scale(), kr.scale())
        for ((jo, ko) in listOf<Pair<() -> Any?, () -> Any?>>( { jr.toString() } to { kr.toString() }, { jr.equals(null) } to { kr.equals(null) }, { jr.negate() } to { kr.negate() }, { jr.plus(null) } to { kr.plus(null) })) {
            val je = runCatching(jo).exceptionOrNull(); val ke = runCatching(ko).exceptionOrNull()
            assertEquals(je?.javaClass, ke?.javaClass)
        }
    }
}
