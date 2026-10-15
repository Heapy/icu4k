package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.AffixUtils as JAffix
import com.ibm.icu.impl.FormattedStringBuilder as JBuilder
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.text.UnicodeSet as JSet
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.UnicodeSet
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class AffixUtilsOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (caught: Throwable) {
        val e = if (caught is InvocationTargetException) caught.targetException else caught
        if (e is IllegalArgumentException && e !is NumberFormatException) "argument:${e.message}" else e.javaClass.simpleName
    }
    private fun patterns(): List<String> {
        val random = Random(5841)
        val pieces = listOf("a", "'", "-", "+", "~", "%", "‰", "¤", "😀", "\ud800", "\udc00", " ", "日", "\u0000")
        return listOf("", "'", "''", ", '", "'abc'", "a''b", "'a'bc'", "¤", "¤¤", "¤¤¤", "¤¤¤¤", "¤¤¤¤¤", "¤¤¤¤¤¤", "¤".repeat(100), "'¤¤'¤", "'a'-'b'", "😀\ud800\udc00") +
            List(1200) { buildString { repeat(random.nextInt(18)) { append(pieces[random.nextInt(pieces.size)]) } } }
    }
    @Test fun tokenizationEscapingQueriesAndReplacementMatchSource() {
        for (pattern in patterns() + listOf(null)) {
            assertEquals(outcome { JAffix.estimateLength(pattern) }, outcome { AffixUtils.estimateLength(pattern) }, "$pattern/estimate")
            assertEquals(JAffix.escape(pattern), AffixUtils.escape(pattern), "$pattern/escape")
            val je = StringBuilder("seed:"); val ke = StringBuilder("seed:")
            assertEquals(JAffix.escape(pattern, je), AffixUtils.escape(pattern, ke))
            assertEquals(je.toString(), ke.toString())
            val jt = mutableListOf<Int>(); val kt = mutableListOf<Int>()
            assertEquals(outcome { JAffix.iterateWithConsumer(pattern) { jt.add(it) } }, outcome { AffixUtils.iterateWithConsumer(pattern) { kt.add(it) } }, "$pattern/iterate")
            assertEquals(jt, kt, "$pattern/partial tokens")
            for (type in listOf(-15, -10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0, 39, 97, 0x1f600)) {
                assertEquals(outcome { JAffix.containsType(pattern, type) }, outcome { AffixUtils.containsType(pattern, type) }, "$pattern/$type/contains")
                assertEquals(outcome { JAffix.replaceType(pattern, type, 'x') }, outcome { AffixUtils.replaceType(pattern, type, 'x') }, "$pattern/$type/replace")
            }
            assertEquals(outcome { JAffix.hasCurrencySymbols(pattern) }, outcome { AffixUtils.hasCurrencySymbols(pattern) }, "$pattern/currency")
            for (set in listOf("[]", "[ a-z]", "[\\u0000-\\U0010ffff]")) {
                assertEquals(outcome { JAffix.containsOnlySymbolsAndIgnorables(pattern, JSet(set)) }, outcome { AffixUtils.containsOnlySymbolsAndIgnorables(pattern, UnicodeSet(set)) }, "$pattern/$set/ignorables")
            }
        }
    }
    @Test fun substitutionCountsFieldsCallbacksAndPartialFailuresMatchSource() {
        val jf = listOf(null, JField.INTEGER, JField.SIGN, JField.CURRENCY, JField.PERCENT, JField.PERMILLE, JField.APPROXIMATELY_SIGN)
        val kf = listOf(null, NumberFormatField.INTEGER, NumberFormatField.SIGN, NumberFormatField.CURRENCY, NumberFormatField.PERCENT, NumberFormatField.PERMILLE, NumberFormatField.APPROXIMATELY_SIGN)
        for (pattern in patterns()) for (mode in 0..2) {
            val jc = mutableListOf<Int>(); val kc = mutableListOf<Int>()
            fun symbol(type: Int): CharSequence? = when (mode) { 0 -> "[$type]"; 1 -> "😀"; else -> if (type == -6) null else "" }
            val jp = JAffix.SymbolProvider { jc.add(it); symbol(it) }; val kp = AffixUtils.SymbolProvider { kc.add(it); symbol(it) }
            val j = JBuilder().apply { append("[]", JField.INTEGER) }; val k = FormattedStringBuilder().apply { append("[]", NumberFormatField.INTEGER) }
            assertEquals(outcome { JAffix.unescape(pattern, j, 1, jp, JField.INTEGER) }, outcome { AffixUtils.unescape(pattern, k, 1, kp, NumberFormatField.INTEGER) }, "$pattern/$mode/unescape")
            assertEquals(j.toString(), k.toString(), "$pattern/$mode/partial output")
            assertEquals(j.toFieldArray().map { jf.indexOf(it) }, k.toFieldArray().map { kf.indexOf(it) }, "$pattern/$mode/fields")
            assertEquals(jc, kc, "$pattern/$mode/provider calls")
            for (length in listOf(false, true)) {
                jc.clear(); kc.clear()
                assertEquals(outcome { JAffix.unescapedCount(pattern, length, jp) }, outcome { AffixUtils.unescapedCount(pattern, length, kp) }, "$pattern/$mode/$length/count")
                assertEquals(jc, kc)
            }
        }
    }
    @Test fun nullableArgumentsShortCircuitsAndFieldTypesMatchSource() {
        for (type in -20..5) {
            assertEquals(outcome { JAffix.getFieldForType(type).toString().substringAfterLast('(') }, outcome { AffixUtils.getFieldForType(type).toString().substringAfterLast('(') }, "$type/field")
        }
        for (pattern in listOf(null, "", "-", "a", "'", "¤¤¤¤¤¤")) {
            val jc = mutableListOf<Int>(); val kc = mutableListOf<Int>()
            val jp = JAffix.SymbolProvider { jc.add(it); "x" }; val kp = AffixUtils.SymbolProvider { kc.add(it); "x" }
            assertEquals(outcome { JAffix.unescape(pattern, null, 0, jp, null) }, outcome { AffixUtils.unescape(pattern, null, 0, kp, null) }, "$pattern/null output")
            assertEquals(jc, kc)
            assertEquals(outcome { JAffix.unescape(pattern, JBuilder(), 0, null, null) }, outcome { AffixUtils.unescape(pattern, FormattedStringBuilder(), 0, null, null) }, "$pattern/null provider")
            assertEquals(outcome { JAffix.iterateWithConsumer(pattern, null) }, outcome { AffixUtils.iterateWithConsumer(pattern, null) }, "$pattern/null consumer")
            assertEquals(outcome { JAffix.containsOnlySymbolsAndIgnorables(pattern, null) }, outcome { AffixUtils.containsOnlySymbolsAndIgnorables(pattern, null) }, "$pattern/null ignorables")
            assertEquals(outcome { JAffix.escape(pattern, null) }, outcome { AffixUtils.escape(pattern, null) }, "$pattern/null escape output")
        }
    }
    @Test fun suppliedCharSequenceCountingMatchesSourceAccesses() {
        class Trace(private val value: String, val accesses: MutableList<String>) : CharSequence {
            override val length: Int get() { accesses.add("length"); return value.length }
            override fun get(index: Int): Char { accesses.add("char:$index"); return value[index] }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = value.subSequence(startIndex, endIndex)
        }
        for (text in listOf("", "a", "😀a\ud800", "\ud800x\udc00")) for (length in listOf(false, true)) {
            val ja = mutableListOf<String>(); val ka = mutableListOf<String>()
            val jt = Trace(text, ja); val kt = Trace(text, ka)
            assertEquals(JAffix.unescapedCount("+", length) { jt }, AffixUtils.unescapedCount("+", length) { kt }, text)
            assertEquals(ja, ka, "$text/accesses")
        }
    }
    @Test fun packedTokenStateMatchesSource() {
        val jNext = JAffix::class.java.getDeclaredMethod("nextToken", Long::class.javaPrimitiveType, CharSequence::class.java).apply { isAccessible = true }
        val jHas = JAffix::class.java.getDeclaredMethod("hasNext", Long::class.javaPrimitiveType, CharSequence::class.java).apply { isAccessible = true }
        val kClass = AffixUtils.Companion::class.java
        val kNext = kClass.getDeclaredMethod("nextToken", Long::class.javaPrimitiveType, CharSequence::class.java).apply { isAccessible = true }
        val kHas = kClass.getDeclaredMethod("hasNext", Long::class.javaPrimitiveType, CharSequence::class.java).apply { isAccessible = true }
        for (pattern in patterns()) {
            var tag = 0L
            while (true) {
                val jh = outcome { jHas.invoke(null, tag, pattern) }; val kh = outcome { kHas.invoke(AffixUtils.Companion, tag, pattern) }
                assertEquals(jh, kh, "$pattern/$tag/has")
                if (jh != true) break
                val jn = outcome { jNext.invoke(null, tag, pattern) }; val kn = outcome { kNext.invoke(AffixUtils.Companion, tag, pattern) }
                assertEquals(jn, kn, "$pattern/$tag/next")
                if (jn !is Long || jn < 0) break
                tag = jn
            }
        }
    }
}
