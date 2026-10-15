// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedNumberFormat as JFormatter
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

/** Real ICU substitutions are test-only dependencies, isolating the newly ported NFRule policy. */
class RbnfRuleCoreOracleTest {
    private val sourceRule = Class.forName("com.ibm.icu.text.NFRule")
    private val sourceSet = Class.forName("com.ibm.icu.text.NFRuleSet")
    private val sourceSub = Class.forName("com.ibm.icu.text.NFSubstitution")
    private val ruleConstructor = sourceRule.getDeclaredConstructor(JFormatter::class.java, String::class.java).also { it.isAccessible = true }
    private fun call(receiver: Any?, type: Class<*>, name: String, vararg args: Any?): Any? {
        val types = generateSequence(type) { it.superclass }.toList()
        val method = types.asSequence().flatMap { it.declaredMethods.asSequence() }.first { method ->
            method.name == name && method.parameterCount == args.size && method.parameterTypes.withIndex().all { (i, t) ->
                val value = args[i]
                if (value == null) !t.isPrimitive else when (t) {
                    java.lang.Long.TYPE -> value is Long
                    java.lang.Integer.TYPE -> value is Int
                    java.lang.Short.TYPE -> value is Short
                    java.lang.Double.TYPE -> value is Double
                    java.lang.Boolean.TYPE -> value is Boolean
                    else -> t.isInstance(value)
                }
            }
        }
        method.isAccessible = true
        return try { method.invoke(receiver, *args) } catch (e: InvocationTargetException) { throw e.targetException }
    }
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        when (cause) {
            is NullPointerException -> "NPE"
            is IndexOutOfBoundsException -> "bounds"
            is ClassCastException -> "cast"
            else -> "${cause.javaClass.simpleName}:${cause.message}"
        }
    }
    private inner class Fixture(fraction: Boolean = false) : RbnfRuleOwner, RbnfRuleSetOwner {
        val source = JFormatter("%test: 0: zero; 1: one; 2: two; 3: three; 4: four; 5: five; 6: six; 7: seven; 8: eight; 9: nine; 10: ten; 20: twenty[->>]; 100: << hundred[ >>];", JLocale.ENGLISH)
        val set = call(source, JFormatter::class.java, "findRuleSet", "%test")!!
        val targetSet = RbnfRuleSetCore(this, arrayOf("%test: fixture"), 0)
        init { if (fraction) { call(set, sourceSet, "makeIntoFractionRuleSet"); targetSet.makeIntoFractionRuleSet() } }
        override fun getDecimalFormatSymbols() = DecimalFormatSymbols(ULocale.ENGLISH)
        override fun getDefaultNaNRule() = RbnfRuleCore(this, "NaN: NaN")
        override fun getDefaultInfinityRule() = RbnfRuleCore(this, "Inf: infinity")
        override fun makeRules(description: String, ruleSet: RbnfRuleSetCore, predecessor: RbnfRule?, rules: MutableList<RbnfRule>) =
            RbnfRuleCore.makeRules(description, ruleSet, predecessor as RbnfRuleCore?, this, rules)
        override fun lenientParseEnabled() = source.lenientParseEnabled()
        override fun getLenientScanner(): RbnfLenientScanner? = null
        override fun createPluralFormat(type: PluralRules.PluralType, pattern: String): PluralFormatCore =
            PluralFormatCore(PluralRules.forLocale(ULocale.ENGLISH, type), DecimalFormat("#,##0.###", getDecimalFormatSymbols())).also { it.applyPattern(pattern) }
        override fun makeSubstitution(position: Int, rule: RbnfRuleCore, predecessor: RbnfRuleCore?, ruleSet: RbnfRuleSetCore, token: String): RbnfSubstitution? {
            val mirror = ruleConstructor.newInstance(source, rule.toString())
            val previous = predecessor?.let { ruleConstructor.newInstance(source, it.toString()) }
            val s = call(null, sourceSub, "makeSubstitution", position, mirror, previous, set, source, token) ?: return null
            val adapter = object : RbnfSubstitution {
                override fun getPos() = call(s, sourceSub, "getPos") as Int
                override fun setDivisor(radix: Int, exponent: Short) { call(s, sourceSub, "setDivisor", radix, exponent) }
                override fun isModulusSubstitution() = call(s, sourceSub, "isModulusSubstitution") as Boolean
                override fun doSubstitution(number: Long, output: StringBuilder?, position: Int, recursionCount: Int) { call(s, s.javaClass, "doSubstitution", number, output, position, recursionCount) }
                override fun doSubstitution(number: Double, output: StringBuilder?, position: Int, recursionCount: Int) { call(s, s.javaClass, "doSubstitution", number, output, position, recursionCount) }
                override fun doParse(text: String?, position: ParsePosition?, baseValue: Double, upperBound: Double, lenient: Boolean, executedMask: Int, recursionCount: Int): Number? {
                    val pp = position?.let { java.text.ParsePosition(it.index).also { p -> p.errorIndex = it.errorIndex } }
                    try { return call(s, s.javaClass, "doParse", text, pp, baseValue, upperBound, lenient, executedMask, recursionCount) as Number? }
                    finally { if (pp != null) { position!!.index = pp.index; position.errorIndex = pp.errorIndex } }
                }
                override fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?) { call(s, s.javaClass, "setDecimalFormatSymbols", symbols?.let { com.ibm.icu.text.DecimalFormatSymbols(JLocale.ENGLISH) }) }
                override fun toString() = s.toString()
            }
            return if (s.javaClass.simpleName == "ModulusSubstitution") object : RbnfModulusSubstitution, RbnfSubstitution by adapter { override fun toString() = adapter.toString() } else adapter
        }
        fun rules(description: String): Pair<List<Any>, List<RbnfRule>> {
            val originals = ArrayList<Any>()
            val targets = ArrayList<RbnfRule>()
            assertEquals(outcome { call(null, sourceRule, "makeRules", description, set, null, source, originals) },
                outcome { RbnfRuleCore.makeRules(description, targetSet, null, this, targets); null }, description)
            val field = sourceSet.getDeclaredField("nonNumericalRules").also { it.isAccessible = true }
            originals.addAll((field.get(set) as Array<*>).filterNotNull())
            targets.addAll(targetSet.nonNumericalRules.filterNotNull())
            assertEquals(originals.map { it.toString() }, targets.map { it.toString() }, description)
            return originals to targets
        }
    }
    @Test fun descriptorAndPowerBoundaries() {
        val source = JFormatter("0: zero;", JLocale.ENGLISH)
        val random = Random(783)
        val descriptions = arrayListOf("", " ", ": text", "foo: text", "1x: text", "-x: minus", "x,x: fraction", "0.x: proper", "x.0: default", "NaN: none", "Inf: infinity", "10/0: bad", "10>>: bad", "10/1: odd", "10/4294967295: bad", "9223372036854775807: large", "18446744073709551616: wrap", "10/18446744073709551616: wrap", "100/2>: binary")
        repeat(200) { descriptions.add("${random.nextLong(0, Long.MAX_VALUE)}/${random.nextInt(2, 100)}: text") }
        for (description in descriptions) {
            var original: Any? = null
            var target: RbnfRuleCore? = null
            assertEquals(outcome { original = ruleConstructor.newInstance(source, description); null }, outcome { target = RbnfRuleCore(null, description); null }, description)
            if (original == null || target == null) continue
            assertEquals(outcome { original.toString() }, outcome { target.toString() }, description)
            for (name in listOf("getBaseValue", "getDivisor", "getDecimalPoint")) {
                val expected = call(original, sourceRule, name)
                val actual = when (name) { "getBaseValue" -> target!!.getBaseValue(); "getDivisor" -> target!!.getDivisor(); else -> target!!.getDecimalPoint() }
                assertEquals(expected, actual, "$description $name")
            }
        }
        for (base in listOf(-1L, 0L, 1L, 2L, 10L, Long.MAX_VALUE)) for (exponent in listOf<Short>(-1, 0, 1, 2, 19, 63, 64, 127, Short.MAX_VALUE)) {
            assertEquals(outcome { call(null, sourceRule, "power", base, exponent) }, outcome { RbnfRuleCore.power(base, exponent) }, "$base^$exponent")
        }
    }
    @Test fun constructionFormattingAndParsingWithRealIcuSubstitutions() {
        val descriptions = listOf(
            "0: zero", "2: ' leading", "100: << hundred[ >>]", "100: [<< hundred >>|a hundred]", "101: << hundred >>",
            "1000/100>: << thousands >>", "10: =0.00= units", "20: twenty[->>];", "20: twenty[->>][tail]",
            "x.x: << point >>", "0.x: point >>", "x.0: <<[ point >>]", "-x: minus >>", "Inf: infinity", "NaN: not a number",
            "100: << $(cardinal,one{hundred}other{hundreds})$[ >>]", "1: $(ordinal,one{first}two{second}few{third}other{th})$",
            "100: prefix[with|without]suffix", "100: [a]b|c", "100: $(unknown,other{bad})$", "100: $(cardinal other{bad})$",
            "100: << broken >", "100: <<< >>", "100: <%missing<", "-x: <<", "100: a<<b>>c<<d"
        )
        val numbers = listOf(-101.0, -1.0, -0.0, 0.01, 0.1, 0.5, 1.0, 2.0, 10.0, 20.0, 21.0, 100.0, 101.0, 110.0, 200.0, 999.5, Double.NaN, Double.POSITIVE_INFINITY)
        for (description in descriptions) {
            val fixture = Fixture()
            val (originals, targets) = fixture.rules(description)
            for ((original, target) in originals.zip(targets)) {
                assertEquals(call(original, sourceRule, "hasModulusSubstitution"), (target as RbnfRuleCore).hasModulusSubstitution(), description)
                val texts = arrayListOf("mismatch", "", "zero trailing", "one hundred twenty-one", "infinity", "not a number", "minus two", "prefixwithsuffix")
                for (number in numbers) {
                    for (asLong in listOf(false, true)) {
                        val sourceOutput = StringBuilder("[]")
                        val targetOutput = StringBuilder("[]")
                        assertEquals(outcome {
                            call(original, sourceRule, "doFormat", if (asLong) number.toLong() else number, sourceOutput, 1, 0)
                        }, outcome {
                            if (asLong) target.doFormat(number.toLong(), targetOutput, 1, 0) else target.doFormat(number, targetOutput, 1, 0)
                            null
                        }, "$description $number long=$asLong")
                        assertEquals(sourceOutput.toString(), targetOutput.toString(), "$description $number long=$asLong")
                        texts.add(sourceOutput.toString().removePrefix("[").removeSuffix("]"))
                    }
                }
                for (text in texts.distinct()) for (fraction in listOf(false, true)) {
                    val sp = java.text.ParsePosition(0).also { it.errorIndex = 17 }
                    val tp = ParsePosition(0).also { it.errorIndex = 17 }
                    assertEquals(outcome { val n = call(original, sourceRule, "doParse", text, sp, fraction, 10000.0, 0, 0); "${n?.javaClass?.name}:$n" },
                        outcome { val n = target.doParse(text, tp, fraction, 10000.0, 0, 0); "${n.javaClass.name}:$n" }, "$description '$text' fraction=$fraction")
                    assertEquals(sp.index, tp.index, "$description '$text'")
                    assertEquals(sp.errorIndex, tp.errorIndex)
                }
            }
        }
    }
    @Test fun fractionRuleExpansionAndSubstitutionRestrictions() {
        for (description in listOf("2: half[s]", "2: << half[s]", "3: <0< thirds", "2: >> halves", "3: [plural|singular]")) {
            Fixture(true).rules(description)
        }
    }
}
