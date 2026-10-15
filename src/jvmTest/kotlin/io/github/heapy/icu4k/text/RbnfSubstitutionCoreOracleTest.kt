// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedNumberFormat as JFormatter
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class RbnfSubstitutionCoreOracleTest {
    private val sourceRule = Class.forName("com.ibm.icu.text.NFRule")
    private val sourceSet = Class.forName("com.ibm.icu.text.NFRuleSet")
    private val sourceSub = Class.forName("com.ibm.icu.text.NFSubstitution")
    private val ctor = sourceRule.getDeclaredConstructor(JFormatter::class.java, String::class.java).also { it.isAccessible = true }
    private fun call(receiver: Any?, type: Class<*>, name: String, vararg args: Any?): Any? {
        val method = generateSequence(type) { it.superclass }.flatMap { it.declaredMethods.asSequence() }.first { m ->
            m.name == name && m.parameterCount == args.size && m.parameterTypes.withIndex().all { (i, t) ->
                val a = args[i]
                if (a == null) !t.isPrimitive else when (t) {
                    java.lang.Long.TYPE -> a is Long
                    java.lang.Integer.TYPE -> a is Int
                    java.lang.Short.TYPE -> a is Short
                    java.lang.Double.TYPE -> a is Double
                    java.lang.Boolean.TYPE -> a is Boolean
                    else -> t.isInstance(a)
                }
            }
        }
        method.isAccessible = true
        return try { method.invoke(receiver, *args) } catch (e: InvocationTargetException) { throw e.targetException }
    }
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when (t) { is NullPointerException -> "NPE"; is IndexOutOfBoundsException -> "bounds"; is ClassCastException -> "cast"; else -> "${t.javaClass.simpleName}:${t.message}" }
    }
    private fun sourceSet(formatter: JFormatter, name: String) = call(formatter, JFormatter::class.java, "findRuleSet", name)!!
    private fun parseSource(sub: Any, text: String?, position: ParsePosition?, base: Double, lenient: Boolean): Any? {
        val pp = position?.let { java.text.ParsePosition(it.index).also { p -> p.errorIndex = it.errorIndex } }
        try { return call(sub, sub.javaClass, "doParse", text, pp, base, 10000.0, lenient, 0, 0) }
        finally { if (pp != null) { position!!.index = pp.index; position.errorIndex = pp.errorIndex } }
    }
    @Test fun everyConcreteSubstitutionTransformsComposesAndFormatsLikeIcu() {
        val descriptions = arrayOf(RbnfTestEngine.ENGLISH, "%%digits: ${RbnfTestEngine.DIGITS}")
        val source = JFormatter(descriptions.joinToString(""), JLocale.ENGLISH)
        val target = RbnfTestEngine(descriptions)
        val set = sourceSet(source, "%main")
        val targetSet = target.findRuleSet("%main")
        val tokens = listOf("", "<<", ">>", ">>>", "<0<", ">00>", "=0.00=", "<%%digits<", ">%%digits>", "=%main=", "==", "<", "<bad<", "<%missing<", "x")
        val longNumbers = listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, -9007199254740993L, -101L, -1L, 0L, 1L, 101L, 9007199254740991L, 9007199254740992L, 9007199254740993L, Long.MAX_VALUE - 1, Long.MAX_VALUE)
        val doubles = listOf(-101.5, -0.5, -0.0, 0.0, 0.01, 0.5, 1.0, 1.5, 101.25, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
        for (descriptor in listOf("101: rule", "-x: negative", "x.x: fraction", "0.x: proper", "x.0: default", "9223372036854775807/2: zero divisor")) for (token in tokens) {
            val sr = ctor.newInstance(source, descriptor)
            val tr = RbnfRuleCore(target, descriptor)
            val predecessor = ctor.newInstance(source, "9: predecessor")
            val tp = RbnfRuleCore(target, "9: predecessor")
            var original: Any? = null
            var actual: RbnfSubstitutionCore? = null
            assertEquals(outcome { original = call(null, sourceSub, "makeSubstitution", 0, sr, predecessor, set, source, token); original.toString() },
                outcome { actual = RbnfSubstitutionCore.makeSubstitution(0, tr, tp, targetSet, target, token); actual.toString() }, "$descriptor $token")
            if (original == null || actual == null) continue
            val s = original!!
            val t = actual!!
            for (n in longNumbers) assertEquals(outcome { call(s, s.javaClass, "transformNumber", n) }, outcome { t.transformNumber(n) }, "$descriptor $token long=$n")
            for (n in doubles) {
                assertEquals(outcome { call(s, s.javaClass, "transformNumber", n) }, outcome { t.transformNumber(n) }, "$descriptor $token double=$n")
                assertEquals(outcome { call(s, s.javaClass, "calcUpperBound", n) }, outcome { t.calcUpperBound(n) }, "$descriptor $token bound=$n")
                for (old in listOf(-1.0, 0.0, 100.0, Double.POSITIVE_INFINITY)) {
                    assertEquals(outcome { call(s, s.javaClass, "composeRuleValue", n, old) }, outcome { t.composeRuleValue(n, old) }, "$descriptor $token compose=$n,$old")
                }
            }
            for (n in listOf(-101.25, -0.0, 0.125, 1.0, 21.0, 101.25, Double.NaN, Double.POSITIVE_INFINITY)) for (asLong in listOf(false, true)) {
                val so = StringBuilder("[]")
                val to = StringBuilder("[]")
                assertEquals(outcome { call(s, s.javaClass, "doSubstitution", if (asLong) n.toLong() else n, so, 1, 0) }, outcome {
                    if (asLong) t.doSubstitution(n.toLong(), to, 1, 0) else t.doSubstitution(n, to, 1, 0)
                    null
                }, "$descriptor $token format=$n long=$asLong")
                assertEquals(so.toString(), to.toString(), "$descriptor $token format=$n long=$asLong")
            }
            for (text in listOf<String?>(null, "", "one", "twenty-one", "one point zero five", "1.25", "zero three", "mismatch")) for (lenient in listOf(false, true)) {
                val sp = ParsePosition(0).also { it.errorIndex = 23 }
                val ap = ParsePosition(0).also { it.errorIndex = 23 }
                assertEquals(outcome { val r = parseSource(s, text, sp, 101.0, lenient); "${r?.javaClass?.name}:$r" }, outcome {
                    val r = t.doParse(text, ap, 101.0, 10000.0, lenient, 0, 0); "${r?.javaClass?.name}:$r"
                }, "$descriptor $token parse=$text lenient=$lenient")
                assertEquals(sp.index, ap.index, "$descriptor $token parse=$text")
                assertEquals(sp.errorIndex, ap.errorIndex, "$descriptor $token parse=$text")
            }
        }
    }
    @Test fun wholeCommonGraphMatchesSourceRuleSets() {
        val examples = listOf(
            arrayOf(RbnfTestEngine.ENGLISH),
            arrayOf("%main: -x: minus >0>; x.x: <0< point >>>; ${RbnfTestEngine.DIGITS} 10: ten[->>]; 20: twenty[->>>]; 100: <0< hundred[ >00>];"),
            arrayOf("%main: x.x: << and >%%fractions>; ${RbnfTestEngine.DIGITS} 10: ten; 20: twenty[ >>]; 100: << hundred[ >>];", "%%fractions: 2: half; 3: <0< thirds; 100: <%main<< hundredths;"),
            arrayOf("%main: 0: =0=; 100: <0< $(cardinal,one{hundred}other{hundreds})$[ >00>];"),
            arrayOf("%main: 0: =0=; 100: <0.00< units;"),
            arrayOf("%main: 0: =0=; 100: <0.00< units >0>;")
        )
        val random = Random(783)
        for (descriptions in examples) {
            val source = JFormatter(descriptions.joinToString(""), JLocale.ENGLISH)
            val target = RbnfTestEngine(descriptions)
            val set = sourceSet(source, "%main")
            val ts = target.findRuleSet("%main")
            val values = mutableListOf(0.0, -0.0, 1.0, 2.0, 0.05, 0.5, 2.0 + 2.0 / 3.0, -101.25, 1234.567, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY)
            repeat(60) { values.add(random.nextInt(-10000, 10000) / 100.0) }
            for (value in values) for (asLong in listOf(false, true)) {
                // Source numerator zero-padding has an unbounded loop for a zero numerator.
                if (descriptions.size > 1 && value != 0.0 && kotlin.math.abs(value) % 1 < 0.005) continue
                val sb = StringBuilder()
                val tb = StringBuilder()
                assertEquals(outcome { call(set, sourceSet, "format", if (asLong) value.toLong() else value, sb, 0, 0) }, outcome {
                    if (asLong) ts.format(value.toLong(), tb, 0, 0) else ts.format(value, tb, 0, 0)
                    null
                }, "${descriptions[0]} format=$value long=$asLong")
                assertEquals(sb.toString(), tb.toString(), "${descriptions[0]} format=$value long=$asLong")
                val text = sb.toString() + " trailing"
                val pp = java.text.ParsePosition(0)
                val tp = ParsePosition(0)
                assertEquals(outcome { val r = call(set, sourceSet, "parse", text, pp, Double.MAX_VALUE, 0, 0); "${r?.javaClass?.name}:$r" }, outcome {
                    val r = ts.parse(text, tp, Double.MAX_VALUE, 0, 0); "${r?.javaClass?.name}:$r"
                }, "${descriptions[0]} parse=$text")
                assertEquals(pp.index, tp.index, "${descriptions[0]} parse=$text")
            }
        }
    }
    @Test fun numeratorFactoryPaddingAndParseState() {
        val descriptions = arrayOf("%main: x.x: >%%fractions>; ${RbnfTestEngine.DIGITS} 10: ten; 20: twenty[ >>]; 100: << hundred[ >>];", "%%fractions: 2: half; 3: <0< thirds; 100: <%main<< hundredths;")
        val source = JFormatter(descriptions.joinToString(""), JLocale.ENGLISH)
        val target = RbnfTestEngine(descriptions)
        val set = sourceSet(source, "%%fractions")
        val targetSet = target.findRuleSet("%%fractions")
        for (denominator in listOf(2L, 3L, 100L)) for (token in listOf("<<", "<0<", "<%main<", "<%main<<", ">>")) {
            val sr = ctor.newInstance(source, "$denominator: fraction")
            val tr = RbnfRuleCore(target, "$denominator: fraction")
            var s: Any? = null
            var t: RbnfSubstitutionCore? = null
            assertEquals(outcome { s = call(null, sourceSub, "makeSubstitution", 0, sr, null, set, source, token); s.toString() },
                outcome { t = RbnfSubstitutionCore.makeSubstitution(0, tr, null, targetSet, target, token); t.toString() }, "$denominator $token")
            if (s == null || t == null) continue
            for (n in listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)) {
                assertEquals(outcome { call(s, s!!.javaClass, "transformNumber", n) }, outcome { t!!.transformNumber(n) })
            }
            for (n in listOf(0.05, 0.125, 0.5, 1.0)) {
                assertEquals(outcome { call(s, s!!.javaClass, "transformNumber", n) }, outcome { t!!.transformNumber(n) })
                // The source zero-padding loop never terminates when the rounded numerator is zero.
                if (token.endsWith("<<") && Math.round(n * denominator) == 0L) continue
                val sb = StringBuilder("[]")
                val tb = StringBuilder("[]")
                assertEquals(outcome { call(s, s!!.javaClass, "doSubstitution", n, sb, 1, 0) }, outcome { t!!.doSubstitution(n, tb, 1, 0); null })
                assertEquals(sb.toString(), tb.toString(), "$denominator $token $n")
            }
            for (text in listOf("", "zero", "one", "five", "zero five", "zero zero five", "1.25", "mismatch")) {
                val sp = ParsePosition(0).also { it.errorIndex = 11 }
                val tp = ParsePosition(0).also { it.errorIndex = 11 }
                assertEquals(outcome { val n = parseSource(s!!, text, sp, denominator.toDouble(), true); "${n?.javaClass?.name}:$n" },
                    outcome { val n = t!!.doParse(text, tp, denominator.toDouble(), 10000.0, true, 0, 0); "${n?.javaClass?.name}:$n" }, "$denominator $token '$text'")
                assertEquals(sp.index, tp.index, "$denominator $token '$text'")
                assertEquals(sp.errorIndex, tp.errorIndex)
            }
        }
    }
    @Test fun multiplierQueriesCurrentRoundingModeAndFailedDivisorUpdatesKeepState() {
        val source = JFormatter(RbnfTestEngine.ENGLISH, JLocale.ENGLISH)
        val target = RbnfTestEngine(arrayOf(RbnfTestEngine.ENGLISH))
        val rule = ctor.newInstance(source, "100: rule")
        val tr = RbnfRuleCore(target, "100: rule")
        val s = call(null, sourceSub, "makeSubstitution", 0, rule, null, sourceSet(source, "%main"), source, "<0.00<")!!
        val t = RbnfSubstitutionCore.makeSubstitution(0, tr, null, target.getDefaultRuleSet(), target, "<0.00<")!!
        for (mode in 0..7) {
            source.setRoundingMode(mode)
            target.configuredRoundingMode = mode
            for (n in listOf(-115.5, 0.5, 115.5, Double.NaN, Double.POSITIVE_INFINITY)) {
                assertEquals(outcome { call(s, s.javaClass, "transformNumber", n) }, outcome { t.transformNumber(n) }, "mode=$mode n=$n")
            }
        }
        for ((radix, exponent) in listOf(10 to 2.toShort(), 2 to 64.toShort(), -1 to 1.toShort(), 10 to (-1).toShort(), 10 to 1.toShort())) {
            assertEquals(outcome { call(s, s.javaClass, "setDivisor", radix, exponent) }, outcome { t.setDivisor(radix, exponent); null })
            assertEquals(outcome { call(s, s.javaClass, "transformNumber", 115L) }, outcome { t.transformNumber(115L) })
        }
    }

}
