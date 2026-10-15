@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.text.PluralRules
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class PluralConditionOracleTest {
    private val parse = PluralRules::class.java.getDeclaredMethod("parseConstraint", String::class.java).apply { isAccessible = true }
    private fun source(text: String): Any? = try { parse.invoke(null, text) } catch (e: InvocationTargetException) { throw e.targetException }
    private fun outcome(block: () -> Any?): String = try { block().toString() } catch (caught: Throwable) {
        val e = if (caught is InvocationTargetException) caught.targetException else caught
        when (e) {
            is java.text.ParseException, is PluralConditionParseException -> "parse:${e.message}"
            is IndexOutOfBoundsException -> "bounds"
            is NumberFormatException -> "number"
            else -> e.javaClass.simpleName
        }
    }
    @Test fun parserCanonicalizationAndMalformedInputMatchSource() {
        val tests = mutableListOf("", " ", "or", "and", "or n=1", "n=1 or and", "n=1 or", "n=1andn=2", "n", "N=1", "n mod", "n % x = 2", "n = 9223372036854775808", "n = -9223372036854775808", "n = ٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣,9007199254740995")
        for (relation in listOf("=", "!=", "is", "is not", "in", "not in", "within", "not within", "in not", "not =", "! in", "is not not")) {
            for (bounds in listOf("1", "1,2", "1..3", "3..1", "1,", "1 2", "1.2", "", "-2..0")) tests.add("n $relation $bounds")
        }
        for (text in tests) assertEquals(outcome { source(text) }, outcome { PluralConditionParser.parse(text) }, text)
    }
    @Test fun evaluationAndLimitednessMatchSource() {
        val expressions = mutableListOf("n", "n=1 or v=2 and f=20", "n % 0 = 2", "n mod -2 = -5..-3", "n=1 or and")
        for (op in PluralRules.Operand.entries) for (relation in listOf("=", "!=", "within", "not within")) for (bounds in listOf("0", "1..3", "0,2..4", "9007199254740993..9223372036854775807")) expressions.add("$op $relation $bounds")
        for (text in expressions) {
            val j = source(text)!!
            val k = PluralConditionParser.parse(text)!!
            val fulfilled = j.javaClass.getDeclaredMethod("isFulfilled", PluralRules.IFixedDecimal::class.java).apply { isAccessible = true }
            val limited = j.javaClass.getDeclaredMethod("isLimited", PluralRules.SampleType::class.java).apply { isAccessible = true }
            for (type in PluralRules.SampleType.entries) assertEquals(outcome { limited.invoke(j, type) }, outcome { k.isLimited(PluralCondition.SampleType.valueOf(type.name)) }, "$text/$type")
            for (n in listOf(-1.0, -0.0, 0.0, 1.0, 1.2, 2.0, 3.5, 4.0, 1e19, Double.NaN, Double.POSITIVE_INFINITY)) for (v in listOf(0, 1, 3)) {
                val jd = PluralRules.FixedDecimal(n, v)
                val kd = FixedDecimalCore(n, v)
                fun sourceEvaluation(): Any? = try { fulfilled.invoke(j, jd) } catch (e: InvocationTargetException) { throw e.targetException }
                assertEquals(outcome { sourceEvaluation() }, outcome { k.evaluate(kd::getPluralOperand) }, "$text/$n/$v")
            }
        }
    }
}
